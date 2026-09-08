package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.job.sys.schedule.enums.ScheduleConcurrentEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 计划执行器：反射调用业务 Bean 方法，并承载"跳过"并发策略
 *
 * <p>并发策略的三条分支中，只有 {@link ScheduleConcurrentEnum#SKIP} 需要本类介入：
 * 抢不到分布式锁即认为上次未结束，直接返回跳过结果；
 * {@link ScheduleConcurrentEnum#ALLOW} 与 {@link ScheduleConcurrentEnum#QUEUE} 分别由
 * Quartz 默认行为与 {@link QueuedMethodInvocationJob} 的注解承载
 *
 * <p>目标方法固定为 public 且无参，存在性在注册期已校验；执行期任何异常都收敛为失败结果，
 * 不向 Quartz 抛出，与"引擎失败不外抛"的整体风格一致
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Slf4j
@Component
public class ScheduleRunner {

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private RedisHelper redisHelper;
    @Resource
    private ScheduleRegistry scheduleRegistry;

    /**
     * 执行一次计划触发
     *
     * <p>按顺序做三道判断：全局维护模式 → 跳过并发策略 → 反射调用。
     * 只有前两道通过才真正执行，被拦下的一律记为跳过
     *
     * @param context Quartz 作业上下文，携带 JobDataMap
     * @return 执行结果，由调用方写入 {@link JobExecutionContext#setResult(Object)}
     */
    public ScheduleRunResult run(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long scheduleId = Long.valueOf(dataMap.getString(ScheduleDataKeys.SCHEDULE_ID));
        String beanName = dataMap.getString(ScheduleDataKeys.BEAN_NAME);
        String methodName = dataMap.getString(ScheduleDataKeys.METHOD_NAME);
        ScheduleConcurrentEnum concurrent = ScheduleConcurrentEnum.parse(
                Integer.valueOf(dataMap.getString(ScheduleDataKeys.CONCURRENT)));

        if (scheduleRegistry.isStandby()) {
            log.info("处于全局维护模式，本次不执行, scheduleId={}, target={}#{}", scheduleId, beanName, methodName);
            return ScheduleRunResult.skip();
        }

        boolean skipStrategy = concurrent == ScheduleConcurrentEnum.SKIP;
        boolean locked = skipStrategy && tryLock(scheduleId);
        if (skipStrategy && !locked) {
            log.warn("计划上次执行未结束，本次跳过, scheduleId={}, target={}#{}", scheduleId, beanName, methodName);
            return ScheduleRunResult.skip();
        }
        try {
            invoke(beanName, methodName);
            return ScheduleRunResult.success();
        } catch (Throwable e) {
            log.error("计划执行失败, scheduleId={}, target={}#{}", scheduleId, beanName, methodName, e);
            return ScheduleRunResult.fail(e);
        } finally {
            if (locked) {
                redisHelper.unlock(lockKey(scheduleId));
            }
        }
    }

    /**
     * 立即尝试加锁，leaseTime 传 null 交给 Redisson 看门狗续期，进程崩溃可自动释放
     */
    private boolean tryLock(Long scheduleId) {
        return redisHelper.tryLock(lockKey(scheduleId), Duration.ZERO, null);
    }

    private String lockKey(Long scheduleId) {
        return ScheduleDataKeys.LOCK_KEY_PREFIX + scheduleId;
    }

    private void invoke(String beanName, String methodName) {
        Object bean = applicationContext.getBean(beanName);
        Method method = ReflectionUtils.findMethod(bean.getClass(), methodName);
        if (method == null) {
            throw new IllegalStateException("目标方法不存在: " + beanName + "#" + methodName + "()");
        }
        ReflectionUtils.makeAccessible(method);
        ReflectionUtils.invokeMethod(method, bean);
    }

}
