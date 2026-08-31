package com.yeungzhy.yeed.api.feign;

import java.lang.annotation.*;

/**
 * 标记一个类为 Feign 客户端的兜底降级实现，支持普通 Fallback 与 FallbackFactory 两种形态。
 *
 * <p>标注本注解的类会被 {@link EnableFeignFallbacks} 经 {@link FeignFallbacksRegistrar}
 * 自动扫描并注册为 Bean：无需 {@code @Component}，也无需在配置类里逐个 {@code @Import}。
 * 必须是具体类（非接口、非抽象类），建议放在 {@code *.feign.fallback} 子包下便于按包扫描。
 *
 * <p>两种形态的取舍：
 * <ul>
 *   <li>普通 Fallback：{@code implements} 目标 {@code @FeignClient} 接口，写法简单，
 *       但触发兜底时拿不到原始异常，无法记录失败原因，线上排查困难</li>
 *   <li>FallbackFactory（推荐）：{@code create(Throwable)} 能拿到触发兜底的原始异常，
 *       打印 ERROR 级堆栈后再返回降级实例，兼顾"前端不抛 500"与"运维可排查"</li>
 * </ul>
 *
 * <p>兜底目标接口的解析与启动期强校验（fail-fast）规则见 {@link FeignFallbacksRegistrar}。
 *
 * <p>使用示例（项目内真实代码）：
 * <pre>{@code
 * // 推荐：FallbackFactory 形态，create 中可拿到触发兜底的原始异常
 * @Slf4j
 * @FeignFallback
 * public class SysUserFeignClientFallbackFactory implements FallbackFactory<SysUserFeignClient> {
 *     private static final SysUserFeignClientFallback FALLBACK = new SysUserFeignClientFallback();
 *     @Override
 *     public SysUserFeignClient create(Throwable cause) {
 *         log.error("Feign 调用触发兜底： {}", cause.getMessage(), cause);
 *         return FALLBACK;
 *     }
 * }
 *
 * // 简写：普通 Fallback 形态，由 Registrar 自动推断兜底目标
 * @FeignFallback
 * public class SysUserFeignClientFallback implements SysUserFeignClient { ... }
 *
 * // 显式写法：同时实现多个 Feign 接口产生歧义时必须用
 * @FeignFallback(SysUserFeignClient.class)
 * public class SysUserFeignClientFallback implements SysUserFeignClient, SysOssFeignClient { ... }
 * }</pre>
 *
 * @author yeungzhy
 * @since 2026-08-10
 * @see EnableFeignFallbacks 开启自动扫描的总开关
 * @see FeignFallbacksRegistrar 目标接口解析与校验规则
 * @see org.springframework.cloud.openfeign.FallbackFactory
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeignFallback {

    /**
     * 本兜底类对应的 {@code @FeignClient} 接口（可选）。
     *
     * <p>默认 {@code void.class} 表示未显式指定，由 Registrar 自动推断；显式指定时跳过推断，
     * 但目标必须标注 {@code @FeignClient}，否则启动期抛异常。需显式指定的典型场景：
     * <ul>
     *   <li>一个兜底类同时实现多个 {@code @FeignClient} 接口（推断歧义）</li>
     *   <li>FallbackFactory 泛型参数无法解析（如误用原始类型 {@code FallbackFactory}）</li>
     *   <li>普通 Fallback 通过继承基类间接实现 Feign 接口（直接接口扫描推断不到）</li>
     * </ul>
     */
    Class<?> value() default void.class;
}
