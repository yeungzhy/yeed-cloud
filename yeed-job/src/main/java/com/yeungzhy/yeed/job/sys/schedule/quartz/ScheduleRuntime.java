package com.yeungzhy.yeed.job.sys.schedule.quartz;

import java.time.LocalDateTime;

/**
 * 计划的运行时触发时间
 *
 * <p>取自 Quartz 的触发器而非数据库：点火时间由 Quartz 计算，落库会形成第二份真相，
 * 且"停用后没有下次时间"这类语义用列存很难表达
 *
 * @param previousFireTime 上次触发时间，从未触发过为 null
 * @param nextFireTime     下次触发时间，停用或已无后续触发为 null
 * @author yeungzhy
 * @since 2026-09-08
 */
public record ScheduleRuntime(LocalDateTime previousFireTime, LocalDateTime nextFireTime) {

    /** 两个时间均未知 */
    public static ScheduleRuntime empty() {
        return new ScheduleRuntime(null, null);
    }

}
