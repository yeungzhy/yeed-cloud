package com.yeungzhy.yeed.job.sys.schedule.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 定时计划配置
 *
 * <p>当前仅含耗时告警阈值：单次执行超过该值时由作业监听器打 WARN 日志，
 * 只告警不中断，中断能力需要业务方法自行响应中断标志
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yeed-job.schedule")
public class ScheduleProperties {

    /**
     * 单次执行耗时的告警阈值，超过则打 WARN 日志
     *
     * <p>裸数字按 {@link DurationUnit} 取秒，显式单位优先，如{@code 30s} / {@code 5m} / {@code PT5M}
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration runTimeWarnThreshold = Duration.ofSeconds(60);

    @AssertTrue(message = "yeed.schedule.run-time-warn-threshold 必须为正数")
    public boolean isRunTimeWarnThresholdPositive() {
        return runTimeWarnThreshold == null
                || !(runTimeWarnThreshold.isNegative() || runTimeWarnThreshold.isZero());
    }

}
