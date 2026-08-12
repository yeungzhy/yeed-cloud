package com.yeungzhy.yeed.api.feign;

import java.lang.annotation.*;

/**
 * 标记一个类为 Feign 客户端的兜底降级实现，支持 <b>普通 Fallback</b> 与 <b>FallbackFactory</b> 两种形态。
 *
 * <p>标注本注解的类会被 {@link EnableFeignFallbacks} 通过 {@link FeignFallbacksRegistrar}
 * 自动扫描并注册为 Bean：无需 {@code @Component}，也无需在配置类里逐个 {@code @Import}。
 * 使用约定：
 * <ul>
 *   <li>必须是具体类（非接口、非抽象类），建议放在 {@code *.feign.fallback} 子包下便于按包扫描</li>
 *   <li>普通 Fallback 仅扫描"直接实现"的接口：若通过继承基类间接实现 Feign 接口，
 *       将无法自动推断目标，需改用 FallbackFactory 或显式指定 {@link #value()}</li>
 *   <li>注册为普通 Bean，实现类中可正常注入其他依赖</li>
 * </ul>
 *
 * <h3>两种形态的取舍</h3>
 * <ul>
 *   <li><b>普通 Fallback</b>：{@code implements} 目标 {@code @FeignClient} 接口并返回固定降级响应。
 *       写法简单，但触发兜底时拿不到原始异常，无法记录失败原因，线上问题排查困难。</li>
 *   <li><b>FallbackFactory</b>（推荐）：{@code implements FallbackFactory<T>}（T 为目标
 *       {@code @FeignClient} 接口），在 {@code create(Throwable)} 中能拿到触发兜底的原始异常，
 *       可打印 ERROR 级堆栈日志后再返回降级实例，兼顾"前端不抛 500"与"运维可排查"。</li>
 * </ul>
 *
 * <h3>兜底目标接口的解析与校验</h3>
 * <p>Registrar 注册时确定本类对应的 {@code @FeignClient} 接口，并在启动期强校验（fail-fast，
 * 不通过直接抛 {@link IllegalStateException}）：
 * <ol>
 *   <li>显式指定 {@link #value()} 时直接采用（目标必须标注 {@code @FeignClient}；
 *       普通 Fallback 必须实现该接口 / FallbackFactory 的泛型 T 必须等于该接口）</li>
 *   <li>未指定时自动推断：
 *       <ul>
 *         <li>FallbackFactory：解析 {@code FallbackFactory<T>} 的泛型参数 T，T 必须标注 {@code @FeignClient}</li>
 *         <li>普通 Fallback：在直接实现的接口中找出标注 {@code @FeignClient} 的接口；
 *             恰好 1 个 → 推断成功；0 个或 ≥2 个 → 抛异常，要求显式指定 {@link #value()}</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>使用示例（以下示例为项目内代码，依赖的类略）：
 * <pre>{@code
 * // 推荐：FallbackFactory 形态（触发兜底时能拿到 Throwable 打堆栈日志）
 * @Slf4j
 * @FeignFallback
 * public class SysUserFeignClientFallbackFactory implements FallbackFactory<SysUserFeignClient> {
 *     private static final SysUserFeignClientFallback FALLBACK = new SysUserFeignClientFallback();
 *     @Override
 *     public SysUserFeignClient create(Throwable cause) {
 *         log.error("Feign 调用触发兜底：yeed-admin/internal/user - {}", cause.getMessage(), cause);
 *         return FALLBACK;
 *     }
 * }
 *
 * // 简写：普通 Fallback 形态，由 Registrar 自动推断兜底目标
 * @FeignFallback
 * public class SysUserFeignClientFallback implements SysUserFeignClient {
 *     @Override
 *     public ApiResult<LoginUserVO> verify(UserVerifyDTO dto) {
 *         return ApiResult.error(CommonCode.REMOTE_SERVICE_ERROR);
 *     }
 * }
 *
 * // 显式写法：同时实现多个 Feign 接口产生歧义时必须用
 * @FeignFallback(SysUserFeignClient.class)
 * public class SysUserFeignClientFallback implements SysUserFeignClient, SomeOtherFeignClient {
 *     // ...
 * }
 * }</pre>
 *
 * @author yeungzhy
 * @since 2026-08-10
 * @see EnableFeignFallbacks 开启自动扫描的总开关
 * @see org.springframework.cloud.openfeign.FallbackFactory
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeignFallback {

    /**
     * 本兜底类对应的 {@code @FeignClient} 接口（可选）。
     *
     * <p>默认 {@code void.class} 表示未显式指定，由 Registrar 自动推断（推断规则见类注释）；
     * 显式指定时跳过推断，但目标必须标注 {@code @FeignClient}，否则启动期抛异常。
     *
     * <p>需要显式指定的典型场景：
     * <ul>
     *   <li>一个兜底类同时实现多个 {@code @FeignClient} 接口（推断歧义）</li>
     *   <li>FallbackFactory 泛型参数无法解析（如误用原始类型 {@code FallbackFactory}）</li>
     *   <li>普通 Fallback 通过继承基类间接实现 Feign 接口（直接接口扫描推断不到）</li>
     * </ul>
     */
    Class<?> value() default void.class;
}
