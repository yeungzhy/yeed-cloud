package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.feign.FeignFallback;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * {@link SysUserFeignClient} 兜底工厂：拿到触发兜底的原始异常打 ERROR 日志，再返回降级实现。
 *
 * <p>选 Factory 而非直接 Fallback：{@code fallback = ...} 触发时拿不到原始异常，线上一旦降级
 * 便无错误日志，难以排查；{@link #create(Throwable)} 拿到异常后可打印堆栈（区分超时 / 拒绝连接 /
 * 下游 5xx），降级响应仍由统一 Fallback 实例返回，保持响应体契约一致。
 *
 * <p>兜底目标由 Registrar 从 {@code FallbackFactory<SysUserFeignClient>} 泛型参数自动推断，
 * 见 {@link com.yeungzhy.yeed.api.feign.FeignFallbacksRegistrar}。
 *
 * @author yeungzhy
 * @since 2026-08-11
 * @see SysUserFeignClientFallback
 */
@Slf4j
@FeignFallback
public class SysUserFeignClientFallbackFactory implements FallbackFactory<SysUserFeignClient> {

    /** 复用 Fallback 实例（无状态线程安全），避免每次触发兜底都 new 一个 */
    private static final SysUserFeignClientFallback FALLBACK = new SysUserFeignClientFallback();

    /**
     * 创建降级实例：每次远程调用失败调用一次，不是每次请求调用一次
     *
     * @param cause 触发兜底的原始异常，不为空；类型随失败原因变化（超时 / 拒绝连接 / 下游错误）
     * @return 复用的无状态降级实例
     */
    @Override
    public SysUserFeignClient create(Throwable cause) {
        log.error("Feign 调用触发兜底： {}", cause.getMessage(), cause);
        return FALLBACK;
    }
}
