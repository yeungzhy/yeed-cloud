package com.yeungzhy.yeed.job.sys.schedule.quartz;

import jakarta.annotation.Resource;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * 反射调用业务 Bean 方法的作业薄壳，承载"允许"与"跳过"两种并发策略
 *
 * <p>本类由 Quartz 自行实例化（不经 Spring 容器），字段注入依赖 Spring Boot 的
 * AutowireCapableBeanJobFactory：它只做属性装配、不走完整创建流程，
 * 因此本类禁止声明 {@code @Transactional}、{@code @Async} 等需要代理的能力
 *
 * <p>执行逻辑全部下沉到 {@link ScheduleRunner}，本类只负责把结果塞回上下文，
 * 供作业监听器落库
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
public class MethodInvocationJob implements Job {

    @Resource
    private ScheduleRunner scheduleRunner;

    /**
     * 执行计划触发
     *
     * @param context Quartz 作业上下文
     */
    @Override
    public void execute(JobExecutionContext context) {
        context.setResult(scheduleRunner.run(context));
    }

}
