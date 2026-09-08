package com.yeungzhy.yeed.gateway.security;

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
 * 网关本地菜单缓存（Caffeine + 精准失效）
 *
 * <p>为什么用本地缓存：网关是 WebFlux 响应式架构，在 Netty event loop 线程上直接走 Redis 是
 * 同步阻塞 I/O，会占用 event loop 线程导致线程饥饿；菜单缓存是「读极多、写极少」的静态配置数据，
 * 完全适合 JVM 本地缓存
 *
 * <p>精准失效：admin 每次重建权限缓存后向 {@link CacheConstant#MENU_CACHE_CHANGED} 主题发布变更事件，
 * 本组件订阅后即时失效本地缓存，权限变更秒级生效，无需等待 TTL；兜底 TTL 仅防御事件丢失
 * （订阅失败/发布失败），事件正常时本地缓存生命周期完全由事件驱动
 *
 * <p>本地 miss 后回源 Redis，回源失败进入 30s 退避窗口，避免 Redis 故障期间每个请求都重复回源打日志
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Slf4j
@Component
public class MenuCache {

    /** 本地缓存兜底 TTL：事件正常时即时失效，仅防御事件丢失导致的永久陈旧 */
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(5);

    /** 回源失败退避时间：故障期间避免每请求都回源 Redis */
    private static final long BACKOFF_MILLIS = 30_000L;

    /** 单例快照键（本地缓存仅存一份） */
    private static final String SNAPSHOT_KEY = "menu-cache-snapshot";

    private final RedisHelper redisHelper;

    private final RedissonClient redissonClient;

    private final Cache<String, MenuCacheSnapshot> localCache;

    /** 最近一次回源失败时间戳（毫秒），用于失败退避 */
    private volatile long lastFailAt = 0L;

    public MenuCache(RedisHelper redisHelper, RedissonClient redissonClient) {
        this.redisHelper = redisHelper;
        this.redissonClient = redissonClient;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(FALLBACK_TTL)
                .build();
    }

    /**
     * 订阅菜单缓存变更事件：admin 重建后发布，收到即失效本地缓存（精准失效）
     *
     * <p>只失效不预热：下次请求本地 miss 才懒加载回源，感知延迟约一次 Redis 往返；
     * 在此同步回源会阻塞 Redisson 事件分发线程，省下的仅毫秒级，不划算
     *
     * <p>订阅失败不阻断启动，事件丢失由兜底 TTL 自愈（最坏 {@link #FALLBACK_TTL} 后回源）
     */
    @PostConstruct
    public void subscribePermsChanged() {
        try {
            RTopic topic = redissonClient.getTopic(CacheConstant.MENU_CACHE_CHANGED);
            topic.addListener(String.class, (channel, message) -> {
                localCache.invalidateAll();
                log.info("收到菜单缓存变更事件，本地缓存已失效");
            });
            log.info("已订阅菜单缓存变更事件：topic={}", CacheConstant.MENU_CACHE_CHANGED);
        } catch (Exception e) {
            log.error("订阅菜单缓存变更事件失败，本地缓存将依赖兜底 TTL 自愈", e);
        }
    }

    /**
     * 获取缓存快照：本地命中直接返回；本地 miss 回源 Redis 全量加载并写入本地
     *
     * @return 权限快照；缓存不可用（未初始化/Redis 异常/退避期内）返回 null，由调用方 fail-closed
     */
    public MenuCacheSnapshot getSnapshot() {
        // 回源失败退避：故障期间直接返回 null，避免每请求重复回源 Redis 打日志
        if (System.currentTimeMillis() - lastFailAt < BACKOFF_MILLIS) {
            return null;
        }
        return localCache.get(SNAPSHOT_KEY, key -> {
            MenuCacheSnapshot snapshot = loadFromRedis();
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
    private MenuCacheSnapshot loadFromRedis() {
        boolean exists = redisHelper.hasKey(CacheConstant.MENU_PERMS_EXACT);
        if (!exists) {
            log.error("菜单接口权限缓存未初始化，或读取缓存失败");
            return null;
        }
        Map<String, String> exactPerms = redisHelper.getMap(CacheConstant.MENU_PERMS_EXACT);
        Map<String, String> exactDesc = redisHelper.getMap(CacheConstant.MENU_DESC_EXACT);
        Map<String, String> antPerms = redisHelper.getMap(CacheConstant.MENU_PERMS_ANT);
        Map<String, String> antDesc = redisHelper.getMap(CacheConstant.MENU_DESC_ANT);
        log.info("菜单缓存已加载到网关本地：精确接口=权限{},描述{}，动态接口=权限{},描述{}",
                exactPerms.size(), exactDesc.size(), antPerms.size(), antDesc.size());
        return new MenuCacheSnapshot(exactPerms, exactDesc, antPerms, antDesc);
    }
}
