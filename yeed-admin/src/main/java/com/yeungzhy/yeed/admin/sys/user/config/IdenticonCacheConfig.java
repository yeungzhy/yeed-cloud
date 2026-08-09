package com.yeungzhy.yeed.admin.sys.user.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Identicon 头像本地缓存配置
 *
 * <p>{@link com.yeungzhy.yeed.common.support.IdenticonUtil#generate(String, int, boolean)}
 * 是确定性纯函数——同一 (id, dark) 永远产出同一 SVG，结果可永久复用且无需失效。
 * 此处用 Caffeine 在 JVM 进程内缓存生成结果，避免每次请求重复做 SHA-256 哈希 + SVG 拼接。
 *
 * <p>选 Caffeine 而非 Redis 的原因：纯函数结果无需跨实例共享，本地内存命中为纳秒级，
 * 远低于 Redis 的网络往返；且 identicon 一旦生成就不变，不存在多节点数据一致性问题。
 *
 * <p>容量与过期策略：
 * <ul>
 *     <li>{@code maximumSize(10_000)}：单个 SVG 很小（约 1~2KB），1 万条约 10~20MB，足够后台系统使用；
 *         超出按 W-TinyLFU 淘汰（比传统 LRU 命中率更高）</li>
 *     <li>{@code expireAfterWrite(7d)}：纯函数本可永不失效，设 7 天仅作为兜底，
 *         防止异常情况下无限堆积；注意是 write 后过期，命中不会续期，避免热点常驻不淘汰</li>
 *     <li>{@code recordStats()}：开启命中统计，排查时可调 {@code cache.stats()} 看 hit rate</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Configuration
public class IdenticonCacheConfig {

    @Bean
    public Cache<String, String> identiconCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofDays(7))
                .recordStats()
                .build();
    }

}
