package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.yeungzhy.yeed.job.sys.schedule.config.ScheduleProperties;
import com.yeungzhy.yeed.job.sys.schedule.log.entity.ScheduleLog;
import com.yeungzhy.yeed.job.sys.schedule.log.enums.ScheduleLogStatusEnum;
import com.yeungzhy.yeed.job.sys.schedule.log.mapper.ScheduleLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 计划执行日志监听器：每次触发结束落一条 {@link ScheduleLog}
 *
 * <p>结果取自 {@link ScheduleRunResult}（执行器写入上下文），
 * 跳过与失败都由执行器收敛为结果对象，故本处只做落库与慢执行告警。
 * 落库失败只记日志，绝不向 Quartz 抛异常，避免影响调度主流程
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Slf4j
@Component
public class ScheduleLogListener implements JobListener {

    /** 触发开始时刻在上下文中的暂存键 */
    private static final String START_TIME = ScheduleLogListener.class.getName() + ".startTime";

    @Resource
    private ScheduleLogMapper scheduleLogMapper;
    @Resource
    private ScheduleProperties scheduleProperties;

    /**
     * 监听器名称，Quartz 用它做注册去重
     *
     * @return 固定名称
     */
    @Override
    public String getName() {
        return "scheduleLogListener";
    }

    /**
     * 暂存开始时刻，用于统计实际耗时（触发器点火时间不包含排队等待）
     *
     * @param context 作业上下文
     */
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        context.put(START_TIME, System.currentTimeMillis());
    }

    /**
     * 未使用触发器否决能力，无需处理
     *
     * @param context 作业上下文
     */
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // 并发策略的"跳过"由执行器的分布式锁实现，不走否决链路
    }

    /**
     * 落库执行日志，并在耗时超阈值时告警
     *
     * @param context      作业上下文
     * @param jobException 作业抛出的异常，执行器不抛异常时恒为 null
     */
    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        try {
            scheduleLogMapper.insert(buildLog(context, jobException));
            warnIfSlow(context);
        } catch (Exception e) {
            log.error("计划执行日志落库失败, jobKey={}", context.getJobDetail().getKey(), e);
        }
    }

    private ScheduleLog buildLog(JobExecutionContext context, JobExecutionException jobException) {
        long startMillis = context.get(START_TIME) instanceof Long value
                ? value
                : context.getFireTime().getTime();
        long duration = System.currentTimeMillis() - startMillis;

        ScheduleRunResult result = context.getResult() instanceof ScheduleRunResult value ? value : null;
        ScheduleLogStatusEnum status = jobException != null || result == null
                ? ScheduleLogStatusEnum.FAIL
                : result.status();
        String errorMsg = result == null ? null : result.errorMsg();

        return new ScheduleLog()
                .setScheduleId(Long.valueOf(context.getMergedJobDataMap().getString(ScheduleDataKeys.SCHEDULE_ID)))
                .setScheduleName(context.getJobDetail().getDescription())
                .setGroupCode(context.getJobDetail().getKey().getGroup())
                .setFireTime(toLocalDateTime(context.getFireTime()))
                .setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault()))
                .setEndTime(LocalDateTime.now())
                .setDurationMs(duration)
                .setStatus(status)
                .setInstanceId(context.getFireInstanceId())
                .setErrorMsg(errorMsg);
    }

    private void warnIfSlow(JobExecutionContext context) {
        long duration = context.getJobRunTime();
        if (duration > scheduleProperties.getRunTimeWarnThreshold().toMillis()) {
            log.warn("计划执行耗时超过阈值, jobKey={}, durationMs={}, threshold={}",
                    context.getJobDetail().getKey(), duration, scheduleProperties.getRunTimeWarnThreshold());
        }
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

}
