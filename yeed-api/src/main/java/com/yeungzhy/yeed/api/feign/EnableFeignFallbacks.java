package com.yeungzhy.yeed.api.feign;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用 Feign 兜底实现的自动扫描与注册。
 *
 * <p>标注在 {@code @Configuration} 类上后，通过 {@code @Import} 导入 {@link FeignFallbacksRegistrar}，
 * 在 Spring 容器初始化阶段扫描 {@link FeignFallback} 注解的类并注册为 Bean，
 * 供 {@code @FeignClient(fallback = ...)} 引用。
 *
 * <p>用法示例：
 * <pre>{@code
 * @Configuration
 * @EnableFeignClients("com.yeungzhy.yeed.api")
 * @EnableFeignFallbacks(basePackages = "com.yeungzhy.yeed.api")
 * public class FeignClientsAutoConfiguration { }
 * }</pre>
 *
 * <p>与 {@code @Import} 逐个导入相比，新增 fallback 时无需修改配置类，
 * 只需在新类上标注 {@link FeignFallback}。
 *
 * @author yeungzhy
 * @since 2026-08-10
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FeignFallbacksRegistrar.class)
public @interface EnableFeignFallbacks {

    String[] value() default {};

    /**
     * 扫描的基础包。
     *
     * <p>未指定时，默认取标注 {@link EnableFeignFallbacks} 的类所在包。
     */
    String[] basePackages() default {};
}
