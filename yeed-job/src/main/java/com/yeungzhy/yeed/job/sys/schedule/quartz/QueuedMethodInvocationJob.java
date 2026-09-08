package com.yeungzhy.yeed.job.sys.schedule.quartz;

import org.quartz.DisallowConcurrentExecution;

/**
 * 排队并发策略的作业壳，与 {@link MethodInvocationJob} 的唯一区别是类级注解
 *
 * <p>Quartz 的并发控制只有类级 {@link DisallowConcurrentExecution}，没有配置项也没有按计划开关，
 * 而并发策略是按计划配置的，故用双作业类：注册时按策略选用本类或父类。
 * 语义是「阻塞期内的触发排队，本次结束后补跑一次」，不是丢弃
 *
 * <p>本类不覆写任何方法，执行链路与父类完全一致
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@DisallowConcurrentExecution
public class QueuedMethodInvocationJob extends MethodInvocationJob {

}
