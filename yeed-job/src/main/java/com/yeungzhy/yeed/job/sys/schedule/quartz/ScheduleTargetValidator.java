package com.yeungzhy.yeed.job.sys.schedule.quartz;

import com.yeungzhy.yeed.common.core.exception.BizAssert;
import com.yeungzhy.yeed.job.sys.schedule.entity.Schedule;
import jakarta.annotation.Resource;
import org.quartz.CronExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.Introspector;
import java.lang.reflect.Method;

/**
 * 计划目标校验器：把不可执行的配置挡在注册之前
 *
 * <p> 校验项包括 CRON 合法性与目标方法的可调用性。注册期 fail-fast 优于执行期报错，
 * 否则一个写错的 Bean 名只会在触发时才暴露，且每次触发都失败
 *
 * @author yeungzhy
 * @since 2026-09-08
 */
@Component
public class ScheduleTargetValidator {

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 把入参的 Bean 名纠正成容器里真实存在的名字
     *
     * <p> 用户习惯双击类名复制粘贴，得到的是首字母大写的 {@code OrderTaskDemo}，
     * 而 Spring 未显式指定时生成的默认 Bean 名是首字母小写的 {@code orderTaskDemo}，原样存库会导致
     * 保存成功但触发时取不到 Bean。这里按 Spring 的规则做一次等价匹配
     *
     * <p> 用 {@link Introspector#decapitalize(String)} 而非手写首字母小写：前两个字符都是大写时它原样返回
     * （如 {@code URLTask}），与 Spring 的 Bean 名生成规则一致
     *
     * <p> 原样匹配优先，故显式指定了大写开头的 Bean 名不会被误改；
     * 转换后仍不存在才报错，最坏情况只是退化成不做纠正
     *
     * @param beanName 待纠正的 Bean 名
     * @return 容器中真实存在的 Bean 名
     */
    public String resolveBeanName(String beanName) {
        BizAssert.isTrue(StringUtils.hasText(beanName), "Spring Bean 名称不能为空");
        if (applicationContext.containsBean(beanName)) {
            return beanName;
        }
        String decapitalized = Introspector.decapitalize(beanName);
        BizAssert.isTrue(applicationContext.containsBean(decapitalized), "Spring Bean 不存在: " + beanName);
        return decapitalized;
    }

    /**
     * 校验计划的可执行性，任一条件不满足即抛业务异常
     *
     * @param schedule 计划定义；Bean 名须先经 {@link #resolveBeanName(String)} 纠正
     */
    public void validate(Schedule schedule) {
        BizAssert.isTrue(StringUtils.hasText(schedule.getCronExpr()), "CRON 表达式不能为空");
        BizAssert.isTrue(CronExpression.isValidExpression(schedule.getCronExpr()), "CRON 表达式非法");
        BizAssert.isTrue(StringUtils.hasText(schedule.getBeanName()), "Spring Bean 名称不能为空");
        BizAssert.isTrue(applicationContext.containsBean(schedule.getBeanName()), "Spring Bean 不存在: " + schedule.getBeanName());

        BizAssert.isTrue(StringUtils.hasText(schedule.getMethodName()), "Bean 方法名不能为空");
        Method method = ReflectionUtils.findMethod(applicationContext.getBean(schedule.getBeanName()).getClass(), schedule.getMethodName());
        BizAssert.isTrue(method != null, "目标方法不存在: " + schedule.getBeanName() + "#" + schedule.getMethodName() + "()");
        BizAssert.isTrue(method.getParameterCount() == 0, "目标方法必须无参，参数请走数据库或配置项");
        BizAssert.isTrue((schedule.getStartTime() == null) == (schedule.getEndTime() == null), "可执行时段的起止时间必须同时填写或同时留空");
    }

}
