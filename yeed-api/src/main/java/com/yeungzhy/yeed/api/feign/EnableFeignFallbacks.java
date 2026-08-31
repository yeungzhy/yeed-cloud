package com.yeungzhy.yeed.api.feign;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启 {@link FeignFallback} 兜底实现的自动扫描与注册。
 *
 * <p>标注在任意 {@code @Configuration} 类（或启动类）上生效：通过 {@link Import} 引入
 * {@link FeignFallbacksRegistrar}，在容器启动的配置解析阶段扫描指定包下所有标注了
 * {@link FeignFallback} 的具体类并注册为 Spring Bean，供
 * {@link org.springframework.cloud.openfeign.FeignClient @FeignClient} 的
 * {@code fallback} / {@code fallbackFactory} 属性按类引用。
 *
 * <p>使用示例（通常与 {@link org.springframework.cloud.openfeign.EnableFeignClients EnableFeignClients}
 * 同标于一个配置类，且扫描范围保持一致）：
 * <pre>{@code
 * @Configuration
 * @EnableFeignClients("com.yeungzhy.yeed.api")
 * @EnableFeignFallbacks("com.yeungzhy.yeed.api")
 * public class FeignClientsAutoConfiguration { }
 * }</pre>
 *
 * <p>不使用本注解时，fallback 类要么依赖组件扫描（扫描范围需与主包重合），要么在配置类中逐个
 * {@code @Import}；本注解把"发现并注册"统一收敛——新增兜底类只需标注 {@link FeignFallback}，
 * 无需再改动任何配置类。
 *
 * @author yeungzhy
 * @since 2026-08-10
 * @see FeignFallback 兜底类标记注解
 * @see FeignFallbacksRegistrar 扫描与注册实现
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FeignFallbacksRegistrar.class)
public @interface EnableFeignFallbacks {

    /**
     * {@link #basePackages()} 的简写别名；两者皆空时默认扫描标注本注解的类所在包。
     */
    String[] value() default {};

    /**
     * 扫描的基础包，可指定多个。
     *
     * <p>解析顺序与 {@link org.springframework.cloud.openfeign.EnableFeignClients} 一致：
     * {@code basePackages} 优先，其次 {@code value}，都未指定则取标注本注解的类所在包。
     */
    String[] basePackages() default {};
}
