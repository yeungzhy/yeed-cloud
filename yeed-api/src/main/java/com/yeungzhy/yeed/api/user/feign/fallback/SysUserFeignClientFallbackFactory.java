package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.feign.FeignFallback;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * {@link SysUserFeignClient} 兜底工厂：拦截触发兜底的原始异常并打 ERROR 日志，再返回降级实现。
 *
 * <p>选 Factory 而非直接 Fallback：{@code fallback = ...} 触发时拿不到原始异常，线上一旦降级
 * 便无错误日志，难以排查；{@link #create(Throwable)} 拿到异常后可打印堆栈（区分超时 / 拒绝连接 /
 * 熔断），降级响应仍由统一 Fallback 实例返回，保持响应体契约一致。
 *
 * <p>兜底目标由 Registrar 从 {@code FallbackFactory<SysUserFeignClient>} 泛型参数自动推断，
 * 见 {@link com.yeungzhy.yeed.api.feign.FeignFallbacksRegistrar}。
 *
 * <p>日志约定：
 * <ul>
 *   <li>ERROR 级别打印完整堆栈，保证日志采集与监控告警能捕获"触发 fallback"事件</li>
 *   <li>失败原因 message 随日志输出，无需翻堆栈首行即可快速判断是超时、拒绝连接还是熔断</li>
 * </ul>
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

    @Override
    public SysUserFeignClient create(Throwable cause) {
        log.error("Feign 调用触发兜底： {}", cause.getMessage(), cause);
        return FALLBACK;
    }
}
