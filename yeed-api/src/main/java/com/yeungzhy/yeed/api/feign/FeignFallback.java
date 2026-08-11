package com.yeungzhy.yeed.api.feign;

import java.lang.annotation.*;

/**
 * 标记一个类为 Feign 客户端的兜底降级实现（支持 <b>普通 Fallback</b> 与 <b>FallbackFactory</b> 两种形态）。
 *
 * <p>标注本注解的类会被 {@link EnableFeignFallbacks} 通过 {@link FeignFallbacksRegistrar}
 * 自动扫描并注册为 Bean，无需标注 {@code @Component}，也无需在配置类里逐个 {@code @Import}。
 *
 * <h3>两种标注形态</h3>
 * <ul>
 *   <li><b>普通 Fallback 类</b>：implements 对应的 {@code @FeignClient} 接口，返回固定降级响应。
 *       优点是写法简单；缺点是触发兜底时拿不到原始异常，无法打印失败原因日志，线上问题排查困难。</li>
 *   <li><b>FallbackFactory 类</b>：implements {@code FallbackFactory<T>}（T 为 {@code @FeignClient} 接口），
 *       在 {@code create(Throwable)} 中能拿到触发兜底的原始异常，可打印 ERROR 级堆栈日志后再返回
 *       Fallback 实例。<b>推荐优先使用此形态</b>，兼顾"前端不抛 500"与"运维可排查"。</li>
 * </ul>
 *
 * <p>使用约定：
 * <ul>
 *   <li>标注类应为具体类（非接口、非抽象类）</li>
 *   <li>建议放置在 {@code *.feign.fallback} 子包下，便于按包扫描</li>
 * </ul>
 *
 * <p><b>兜底目标接口的解析</b>：Registrar 注册时会先确定兜底目标 Feign 客户端接口：
 * <ol>
 *   <li>若 {@link #value()} 显式指定，直接采用（同时校验指定接口必须标注 {@code @FeignClient}）</li>
 *   <li>否则自动推断：
 *       <ul>
 *         <li>FallbackFactory 形态：解析 {@code FallbackFactory<T>} 的泛型参数 T，T 必须标注 {@code @FeignClient}</li>
 *         <li>普通 Fallback 形态：从标注类实现的接口中找出被 {@code @FeignClient} 标注的接口
 *             <ul>
 *               <li>恰好 1 个 → 推断成功</li>
 *               <li>0 个 → 抛 {@link IllegalStateException}（未实现任何 Feign 契约）</li>
 *               <li>多个 → 抛 {@link IllegalStateException}（有歧义，需显式指定 {@link #value()}）</li>
 *             </ul>
 *         </li>
 *       </ul>
 *   </li>
 * </ol>
 * 解析完成后均会强校验：标注类必须满足契约关系（普通 Fallback implements 目标接口 / FallbackFactory 的 T 与目标一致），
 * 否则抛 {@link IllegalStateException}（启动期 fail-fast，不可关闭）。
 *
 * <p>使用示例：
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
 * // 显式写法：多接口歧义时必须用（无论是 Fallback 还是 FallbackFactory 都适用）
 * @FeignFallback(SysUserFeignClient.class)
 * public class SysUserFeignClientFallback implements SysUserFeignClient, SomeOtherFeignClient {
 *     // ...
 * }
 * }</pre>
 *
 * @author yeungzhy
 * @since 2026-08-10
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeignFallback {

    /**
     * 该 fallback / fallbackFactory 对应的 Feign 客户端接口（可选）。
     *
     * <p>显式指定时，Registrar 直接以此作为兜底目标，跳过自动推断；
     * 未指定（默认 {@code void.class}）时，Registrar 根据标注类型自动推断：
     * <ul>
     *   <li>FallbackFactory 形态：解析 {@code FallbackFactory<T>} 的泛型参数 T</li>
     *   <li>普通 Fallback 形态：扫描直接实现的接口中被 {@code @FeignClient} 标注的接口</li>
     * </ul>
     *
     * <p>无论显式还是推断，Registrar 都会强校验：
     * <ul>
     *   <li>显式指定时，目标接口必须标注 {@code @FeignClient}</li>
     *   <li>普通 Fallback：必须实现该接口</li>
     *   <li>FallbackFactory：泛型参数 T 必须等于目标接口</li>
     * </ul>
     * 否则抛 {@link IllegalStateException}（启动期 fail-fast，不可关闭）。
     *
     * <p>使用场景：
     * <ul>
     *   <li>单 Feign 契约场景：可省略 {@code value()}，让 Registrar 自动推断（99% 的情况）</li>
     *   <li>多 Feign 契约歧义场景（标注类同时实现多个 Feign 接口 / 泛型参数不可解析时）：
     *       必须显式指定 {@code value()} 消歧</li>
     * </ul>
     */
    Class<?> value() default void.class;
}
