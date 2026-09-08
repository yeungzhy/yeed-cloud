package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.yeungzhy.yeed.job.sys.schedule.log.enums.ScheduleLogStatusEnum;

/**
 * 单次触发的执行结果
 *
 * <p> 由 {@link ScheduleRunner} 写入 {@link org.quartz.JobExecutionContext#setResult(Object)}，
 * 供作业监听器落库；作业自身不向 Quartz 抛异常，避免触发框架级重试
 *
 * @param status   执行结果
 * @param errorMsg 异常摘要，仅 {@link ScheduleLogStatusEnum#FAIL} 有值
 * @author yeungzhy
 * @since 2026-09-08
 */
public record ScheduleRunResult(ScheduleLogStatusEnum status, String errorMsg) {

    /** 异常摘要落库上限，与 error_msg 列长度保持一致 */
    private static final int ERROR_MSG_MAX = 2000;

    /** 成功 */
    public static ScheduleRunResult success() {
        return new ScheduleRunResult(ScheduleLogStatusEnum.SUCCESS, null);
    }

    /** 跳过 */
    public static ScheduleRunResult skip() {
        return new ScheduleRunResult(ScheduleLogStatusEnum.SKIP, null);
    }

    /** 失败，异常摘要按列长截断 */
    public static ScheduleRunResult fail(Throwable e) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        return new ScheduleRunResult(ScheduleLogStatusEnum.FAIL,
                msg.length() > ERROR_MSG_MAX ? msg.substring(0, ERROR_MSG_MAX) : msg);
    }

}
