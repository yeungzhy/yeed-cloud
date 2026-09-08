package com.yeungzhy.yeed.job.sys.schedule.quartz;

/**
 * 计划触发在 {@link org.quartz.JobDataMap} 中传递的键名
 *
 * <p>开启 {@code org.quartz.jobStore.useProperties} 后 JobDataMap 只接受 String，
 * 故数值一律以字符串存放，读取端自行转换
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
public final class ScheduleDataKeys {

    /** 计划主键 */
    public static final String SCHEDULE_ID = "scheduleId";
    /** 目标 Spring Bean 名称 */
    public static final String BEAN_NAME = "beanName";
    /** 目标方法名 */
    public static final String METHOD_NAME = "methodName";
    /** 并发策略（存 {@link com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum} 的 code） */
    public static final String CONCURRENT = "concurrent";

    /** 分布式锁 key 前缀，RedisHelper 会再补 {@code yeed:lock:} */
    public static final String LOCK_KEY_PREFIX = "schedule:run:";

    private ScheduleDataKeys() {
    }

}
