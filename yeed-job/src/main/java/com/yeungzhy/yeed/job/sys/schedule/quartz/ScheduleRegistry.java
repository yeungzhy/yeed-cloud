package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleMisfireEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.calendar.DailyCalendar;
import org.quartz.impl.matchers.EverythingMatcher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 计划注册表：把 {@link Schedule} 同步到 Quartz，是控制面唯一出口
 *
 * <p>所有写操作都按 JobKey / TriggerKey 幂等执行，两个 job 实例同时启动装载不会重复注册，
 * Quartz 的 JDBC 存储自身有行锁，无需额外的分布式协调。
 * 本注册表只做新增与覆盖，不清理库中多余的定义，避免误删同伴实例正在使用的作业
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Slf4j
@Component
public class ScheduleRegistry {

    /** 未配置分组的计划归入该组，Quartz 的分组不允许为空 */
    private static final String DEFAULT_GROUP = "DEFAULT";

    /** 每日时段窗口的 Quartz 日历名前缀，后缀接计划主键 */
    private static final String CALENDAR_PREFIX = "scheduleWindow:";

    /**
     * 全局维护模式标记，放 Redis 而非 {@link Scheduler#standby()}：
     * standby 只作用于调用它的那个实例，集群下另一个实例照常触发，达不到"全局"效果
     */
    private static final String STANDBY_KEY = "schedule:standby";

    /** DailyCalendar 接受的时间格式 */
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Resource
    private Scheduler scheduler;
    @Resource
    private ScheduleLogListener scheduleLogListener;
    @Resource
    private RedisHelper redisHelper;

    /**
     * 注册作业监听器，执行日志的落库入口
     *
     * @throws SchedulerException 注册失败时启动即失败，避免静默丢失日志
     */
    @PostConstruct
    public void registerLogListener() throws SchedulerException {
        scheduler.getListenerManager().addJobListener(scheduleLogListener, EverythingMatcher.allJobs());
    }

    /**
     * 新增或覆盖计划的作业与触发器，并按状态暂停或恢复
     * <p>Quartz 侧失败只记日志不向外抛：数据库是真源，下次启动装载会自愈
     *
     * @param schedule 计划定义
     */
    public void upsert(Schedule schedule) {
        try {
            JobKey jobKey = jobKey(schedule);
            scheduler.addJob(buildJobDetail(schedule, jobKey), true);

            // 日历必须先于触发器存在，否则触发器引用了一个不存在的日历名会注册失败
            String calendarName = applyWindowCalendar(schedule);
            TriggerKey triggerKey = triggerKey(schedule);
            Trigger trigger = buildTrigger(schedule, jobKey, triggerKey, calendarName);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
            applyStatus(jobKey, schedule.getStatus());
            // RuntimeException 一并兜住：CRON 非法、Bean 缺失等由注册前的校验拦下，
            // 这里只兜住存量脏数据，避免单条坏计划拖垮整个启动装载
        } catch (SchedulerException | RuntimeException e) {
            log.error("计划注册到 Quartz 失败, scheduleId={}, name={}", schedule.getId(), schedule.getName(), e);
        }
    }

    /**
     * 注销计划的触发器与作业
     *
     * <p>返回成败供调用方决定后续动作：调用方据此判断能否删库，
     * 否则会出现"库里没了、Quartz 里还在"的隐形孤儿
     *
     * @param schedule 计划定义
     * @return true 注销成功；false 注销失败，调用方不应删除库记录
     */
    public boolean remove(Schedule schedule) {
        try {
            scheduler.unscheduleJob(triggerKey(schedule));
            scheduler.deleteJob(jobKey(schedule));
            scheduler.deleteCalendar(calendarName(schedule));
            return true;
        } catch (SchedulerException e) {
            log.error("计划从 Quartz 注销失败, scheduleId={}, name={}", schedule.getId(), schedule.getName(), e);
            return false;
        }
    }

    /**
     * 读取计划的运行时触发时间，Quartz 是这部分数据的唯一权威
     *
     * <p>停用的计划 {@code nextFireTime} 恒为 null，这正是要展示给前端的语义
     *
     * @param schedule 计划定义
     * @return 上次与下次触发时间，取不到时两个字段均为 null
     */
    public ScheduleRuntime runtime(Schedule schedule) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(schedule));
            if (trigger == null) {
                return ScheduleRuntime.empty();
            }
            return new ScheduleRuntime(toLocalDateTime(trigger.getPreviousFireTime()),
                    toLocalDateTime(trigger.getNextFireTime()));
        } catch (SchedulerException e) {
            log.error("查询计划触发时间失败, scheduleId={}", schedule.getId(), e);
            return ScheduleRuntime.empty();
        }
    }

    /**
     * 判断是否处于全局维护模式
     *
     * <p>Redis 不可用时按非维护模式处理（fail-open），避免缓存抖动导致全站计划停摆
     *
     * @return true 处于维护模式
     */
    public boolean isStandby() {
        return Boolean.TRUE.equals(redisHelper.get(STANDBY_KEY));
    }

    /**
     * 切换全局维护模式，标记落 Redis 供全部实例共享
     *
     * @param standby true 进入维护模式，false 恢复调度
     */
    public void changeStandby(boolean standby) {
        if (standby) {
            redisHelper.set(STANDBY_KEY, Boolean.TRUE, null);
            return;
        }
        redisHelper.delete(STANDBY_KEY);
    }

    /**
     * 同步每日时段窗口对应的 Quartz 日历
     *
     * <p>窗口由 {@link DailyCalendar} 承载并取反（默认语义是"排除该区间"，取反后才是"只在区间内"）。
     * 未配置时段时必须清掉日历并把触发器上的日历名置空，残留引用会让计划永远不触发
     *
     * @return 日历名；无时段限制时返回 null
     */
    private String applyWindowCalendar(Schedule schedule) throws SchedulerException {
        String calendarName = calendarName(schedule);
        if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
            scheduler.deleteCalendar(calendarName);
            return null;
        }
        DailyCalendar calendar = new DailyCalendar(schedule.getStartTime().format(TIME_OF_DAY),
                schedule.getEndTime().format(TIME_OF_DAY));
        calendar.setInvertTimeRange(true);
        scheduler.addCalendar(calendarName, calendar, true, true);
        return calendarName;
    }

    private String calendarName(Schedule schedule) {
        return CALENDAR_PREFIX + schedule.getId();
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 切换启停：停用走暂停而非删除，保留触发器的点火时间，重新启用可立即接续
     *
     * @param schedule 计划定义，状态取自 {@link Schedule#getStatus()}
     */
    public void changeStatus(Schedule schedule) {
        try {
            applyStatus(jobKey(schedule), schedule.getStatus());
        } catch (SchedulerException e) {
            log.error("计划启停切换失败, scheduleId={}, name={}", schedule.getId(), schedule.getName(), e);
        }
    }

    /**
     * 立即执行一次，不影响原有的 CRON 触发器
     *
     * @param schedule 计划定义
     */
    public void triggerOnce(Schedule schedule) {
        try {
            scheduler.triggerJob(jobKey(schedule), buildDataMap(schedule));
        } catch (SchedulerException e) {
            log.error("计划立即执行失败, scheduleId={}, name={}", schedule.getId(), schedule.getName(), e);
        }
    }

    private void applyStatus(JobKey jobKey, EnableStatusEnum status) throws SchedulerException {
        if (EnableStatusEnum.isDisabled(status)) {
            scheduler.pauseJob(jobKey);
        } else {
            scheduler.resumeJob(jobKey);
        }
    }

    private JobDetail buildJobDetail(Schedule schedule, JobKey jobKey) {
        return JobBuilder.newJob(jobClass(schedule))
                .withIdentity(jobKey)
                .withDescription(schedule.getName())
                .setJobData(buildDataMap(schedule))
                // addJob 要求作业是持久的，否则没有触发器引用时会被自动清理
                .storeDurably()
                .build();
    }

    private Trigger buildTrigger(Schedule schedule, JobKey jobKey, TriggerKey triggerKey, String calendarName) {
        CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule(schedule.getCronExpr());
        // 补跑一次：重启后立即触发一次再回到正常周期，适合"每天必须跑一次"的对账类计划
        if (schedule.getMisfireInstr() == ScheduleMisfireEnum.FIRE_ONCE) {
            cron = cron.withMisfireHandlingInstructionFireAndProceed();
        } else {
            cron = cron.withMisfireHandlingInstructionDoNothing();
        }
        // withSchedule 会把泛型收窄为 CronTrigger，故变量类型必须跟它一致，不能声明成 Trigger
        TriggerBuilder<CronTrigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(cron);
        if (calendarName != null) {
            builder.modifiedByCalendar(calendarName);
        }
        return builder.build();
    }

    private JobDataMap buildDataMap(Schedule schedule) {
        JobDataMap dataMap = new JobDataMap();
        // useProperties=true 下只接受字符串，数值统一按字符串存
        dataMap.put(ScheduleDataKeys.SCHEDULE_ID, String.valueOf(schedule.getId()));
        dataMap.put(ScheduleDataKeys.BEAN_NAME, schedule.getBeanName());
        dataMap.put(ScheduleDataKeys.METHOD_NAME, schedule.getMethodName());
        dataMap.putAsString(ScheduleDataKeys.CONCURRENT, code(schedule.getAllowConcurrent()));
        return dataMap;
    }

    private Class<? extends Job> jobClass(Schedule schedule) {
        return schedule.getAllowConcurrent() == ScheduleConcurrentEnum.QUEUE
                ? QueuedMethodInvocationJob.class
                : MethodInvocationJob.class;
    }

    private int code(ScheduleConcurrentEnum concurrent) {
        return concurrent == null ? ScheduleConcurrentEnum.ALLOW.getCode() : concurrent.getCode();
    }

    private JobKey jobKey(Schedule schedule) {
        return JobKey.jobKey(String.valueOf(schedule.getId()), group(schedule));
    }

    private TriggerKey triggerKey(Schedule schedule) {
        return TriggerKey.triggerKey(String.valueOf(schedule.getId()), group(schedule));
    }

    private String group(Schedule schedule) {
        return schedule.getGroupCode() == null || schedule.getGroupCode().isBlank()
                ? DEFAULT_GROUP
                : schedule.getGroupCode();
    }

}
