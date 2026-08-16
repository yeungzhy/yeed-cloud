package com.yeungzhy.yeed.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 网关本地菜单接口权限缓存（Caffeine + 精准失效）
 *
 * <p><b>为什么需要本地缓存：</b>网关是 WebFlux 响应式架构，鉴权热路径（SaReactorFilter
 * 的 auth 回调）运行在 Netty event loop 线程上，直接走 Redis 是同步阻塞 I/O，会占用
 * event loop 线程导致线程饥饿。菜单接口权限是「读极多、写极少」的静态配置数据
 * （仅菜单增删改时重建，约 29 个节点），完全适合 JVM 本地缓存。
 *
 * <p><b>精准失效（变更事件）：</b>admin 每次重建权限缓存（{@code reloadPermsCache}）后
 * 向 {@link CacheConstant#SYS_MENU_API_PERMS_CHANGED} 主题发布变更事件，本组件订阅后
 * 即时失效本地缓存，权限变更秒级生效，无需等待 TTL。兜底 TTL 仅防御事件丢失
 * （订阅失败/发布失败），事件正常时本地缓存生命周期完全由事件驱动。
 *
 * <p>本地 miss 后回源 Redis，回源失败后进入退避窗口（30s），避免 Redis 故障期间每个请求都重复回源打日志
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Component
public class GatewayApiPermsCache {

    /** 本地缓存兜底 TTL：事件正常时即时失效，仅防御事件丢失导致的永久陈旧 */
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(5);

    /** 回源失败退避时间：故障期间避免每请求都回源 Redis */
    private static final long BACKOFF_MILLIS = 30_000L;

    /** 单例快照键（本地缓存仅存一份） */
    private static final String SNAPSHOT_KEY = "api-perms-snapshot";

    private final RedisHelper redisHelper;

    private final RedissonClient redissonClient;

    private final Cache<String, ApiPermsSnapshot> localCache;

    /** 最近一次回源失败时间戳（毫秒），用于失败退避 */
    private volatile long lastFailAt = 0L;

    public GatewayApiPermsCache(RedisHelper redisHelper, RedissonClient redissonClient) {
        this.redisHelper = redisHelper;
        this.redissonClient = redissonClient;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(FALLBACK_TTL)
                .build();
    }

    /**
     * 订阅权限缓存变更事件：admin 重建后发布，收到即失效本地缓存（精准失效）
     *
     * <p>订阅失败不阻断启动：事件丢失由兜底 TTL 自愈（最坏 5 分钟后回源自愈）
     */
    @PostConstruct
    public void subscribePermsChanged() {
        try {
            RTopic topic = redissonClient.getTopic(CacheConstant.SYS_MENU_API_PERMS_CHANGED);
            topic.addListener(String.class, (channel, message) -> {
                localCache.invalidateAll();
                log.info("收到菜单接口权限缓存变更事件，本地缓存已失效");
            });
            log.info("已订阅菜单接口权限缓存变更事件：topic={}", CacheConstant.SYS_MENU_API_PERMS_CHANGED);
        } catch (Exception e) {
            log.warn("订阅菜单接口权限缓存变更事件失败，本地缓存将依赖兜底 TTL 自愈：{}", e.getMessage());
        }
    }

    /**
     * 获取权限快照：本地命中直接返回；本地 miss 回源 Redis 全量加载并写入本地
     *
     * @return 权限快照；缓存不可用（未初始化/Redis 异常/退避期内）返回 null，由调用方 fail-closed
     */
    public ApiPermsSnapshot getSnapshot() {
        // 回源失败退避：故障期间直接返回 null，避免每请求重复回源 Redis 打日志
        if (System.currentTimeMillis() - lastFailAt < BACKOFF_MILLIS) {
            return null;
        }
        return localCache.get(SNAPSHOT_KEY, key -> {
            ApiPermsSnapshot snapshot = loadFromRedis();
            if (snapshot == null) {
                lastFailAt = System.currentTimeMillis();
            }
            return snapshot;
        });
    }

    /**
     * 从 Redis 回源加载全量权限快照（精确 + 通配两个键）
     *
     * @return 快照；缓存不可用返回 null
     */
    private ApiPermsSnapshot loadFromRedis() {
        boolean exists = redisHelper.hasKey(CacheConstant.SYS_MENU_API_PERMS_ALL);
        if (!exists) {
            log.error("菜单接口权限缓存未初始化,或读取缓存失败");
            return null;
        }
        Map<String, String> exact = redisHelper.getMap(CacheConstant.SYS_MENU_API_PERMS_ALL);
        Map<String, String> ant = redisHelper.getMap(CacheConstant.SYS_MENU_API_PERMS_ALL_ANT);
        log.info("菜单接口权限缓存已加载到网关本地：精确接口={}，动态接口={}", exact.size(), ant.size());
        return new ApiPermsSnapshot(exact, ant);
    }
}
