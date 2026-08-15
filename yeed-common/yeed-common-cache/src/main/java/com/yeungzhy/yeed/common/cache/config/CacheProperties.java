package com.yeungzhy.yeed.common.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存默认配置
 *
 * <p>当前仅含默认过期时间：{@link com.yeungzhy.yeed.common.cache.support.RedisHelper}
 * 的写方法在不显式传过期时间时使用该值，避免数据永久堆积
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Data
@ConfigurationProperties(prefix = "yeed.cache")
public class CacheProperties {

    /**
     * 写方法不显式传过期时间时采用的默认过期时长
     *
     * <p>配置项 {@code yeed.cache.default-ttl}，如 {@code 12h} / {@code 30m}；
     * 未配置时默认 24 小时
     */
    private Duration defaultTtl = Duration.ofHours(24);

}
