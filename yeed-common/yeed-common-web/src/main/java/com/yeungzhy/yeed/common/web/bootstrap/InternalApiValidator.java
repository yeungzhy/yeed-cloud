package com.yeungzhy.yeed.common.web.bootstrap;

import com.yeungzhy.yeed.common.web.annotation.InternalApi;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Map;

/**
 * 启动期契约守卫：校验内部 RPC-Style 端点"路径前缀与注解成对出现"
 *
 * <p> 内部契约（RPC-Style，见 {@link InternalApi}）成立依赖两个事实同时为真
 * <ul>
 *   <li>端点挂在 {@code /internal/**} 前缀下，网关不对外路由、仅服务间 Feign 可达
 *   <li>类标注 {@code @InternalApi}，它是
 *       {@link com.yeungzhy.yeed.common.web.exception.InternalApiExceptionHandler}
 *       接管内部端点异常的依据；漏标注时内部异常会退化成 HTTP 200 假成功，消费方
 *       收不到错误码也拿不到话术，一律降级为"系统繁忙"
 * </ul>
 * 故校验范围是类级 {@code @RequestMapping}（遍历全部 {@code @Controller} / {@code @RestController}
 * Bean），三类不一致全部启动失败：路径含 {@code /internal/**} 却未标注解、
 * 已标注解但路径不含该前缀、以及同一类同时存在内部与外部路径（注解按类生效，
 * 混用必然破坏其中一种错误契约）
 *
 * <p> 执行时机与装配：实现 {@link SmartInitializingSingleton}，所有非懒加载单例实例化完成后
 * 执行一次，抛异常即启动失败（fail-fast，先于 Web 容器就绪）；
 * 由 {@link com.yeungzhy.yeed.common.web.config.InternalApiValidatorAutoConfiguration}
 * 经 {@code @Bean} 注册并由 {@code AutoConfiguration.imports} 加载，否则守卫静默失效
 *
 * <p> 校验边界：仅识别 {@code /internal} 前缀且只看类级映射；方法级映射随类前缀继承，
 * 无法逃出本校验，将来若放开其它内部前缀需同步扩展判定逻辑
 *
 * @author yeungzhy
 * @since 2026-09-04
 * @see InternalApi
 */
public class InternalApiValidator implements SmartInitializingSingleton {

    /**
     * 内部端点统一前缀：网关路由与消费方 Feign 调用约定共用，改动需全链路同步
     */
    private static final String INTERNAL_PREFIX = "/internal";

    private final ApplicationContext applicationContext;

    public InternalApiValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 所有单例实例化完成后执行契约校验（执行时机与失败语义见类注释）
     */
    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(Controller.class);
        for (Object bean : controllers.values()) {
            // 解 CGLIB 代理取目标类，保证能读到类上真实映射与注解
            validateController(ClassUtils.getUserClass(bean));
        }
    }

    // ==================== 私有辅助 ====================

    private void validateController(Class<?> clazz) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
        if (mapping == null) {
            return;
        }
        String[] paths = mapping.value();
        if (paths.length == 0) {
            paths = mapping.path();
        }
        if (paths.length == 0) {
            // 类级 @RequestMapping 未声明路径（如仅限定 method/header），无校验对象
            return;
        }

        boolean hasInternal = false;
        boolean hasExternal = false;
        for (String path : paths) {
            if (isInternalPath(path)) {
                hasInternal = true;
            } else {
                hasExternal = true;
            }
        }
        if (hasInternal && hasExternal) {
            throw new IllegalStateException(String.format(
                    "启动校验失败：Controller [%s] 类级映射 %s 同时包含内部与外部路径，请拆分为独立的内部 / 外部 Controller",
                    clazz.getName(), Arrays.toString(paths)));
        }

        boolean hasInternalApi = AnnotatedElementUtils.hasAnnotation(clazz, InternalApi.class);
        if (hasInternal && !hasInternalApi) {
            throw new IllegalStateException(String.format(
                    "启动校验失败：Controller [%s] 类级映射 %s 位于 %s/** 却未标注 @InternalApi",
                    clazz.getName(), Arrays.toString(paths), INTERNAL_PREFIX));
        }
        if (hasExternal && hasInternalApi) {
            throw new IllegalStateException(String.format(
                    "启动校验失败：Controller [%s] 标注了 @InternalApi，但类级映射 %s 不在 %s/** 下，RPC-Style 端点不得挂在对外路径上，请改用 %s 前缀",
                    clazz.getName(), Arrays.toString(paths), INTERNAL_PREFIX, INTERNAL_PREFIX));
        }
    }

    /**
     * 是否落在内部端点前缀下（按路径段边界判断，避免 /internalXxx 之类前缀误伤）
     */
    private boolean isInternalPath(String path) {
        return path.equals(INTERNAL_PREFIX) || path.startsWith(INTERNAL_PREFIX + "/");
    }

}
