package com.yeungzhy.yeed.common.cache.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

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
@Validated
@ConfigurationProperties(prefix = "yeed.cache")
public class CacheProperties {

    /**
     * 写方法不显式传过期时间时采用的默认过期时长
     *
     * <p>裸数字按 {@link DurationUnit} 取小时，显式单位优先，如{@code 30m} / {@code 12h} / {@code PT12H}
     */
    @DurationUnit(ChronoUnit.HOURS)
    private Duration defaultTtl = Duration.ofHours(24);

    @AssertTrue(message = "yeed.cache.default-ttl 必须为正数")
    public boolean isDefaultTtlPositive() {
        return defaultTtl == null || !(defaultTtl.isNegative() || defaultTtl.isZero());
    }

}
