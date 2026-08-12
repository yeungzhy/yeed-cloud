package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.feign.FeignFallback;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * {@link SysUserFeignClient} 兜底工厂：拦截原始异常后打日志，再交给具体 Fallback 实现返回降级响应。
 *
 * <p>之所以引入 Factory 而非直接使用 {@link SysUserFeignClientFallback}：
 * {@code fallback = ...} 形式在触发兜底时拿不到原始异常，线上一旦触发 fallback 没有任何错误日志，排查困难。
 * 改用 {@code fallbackFactory = ...} 后，{@link #create(Throwable)} 会拿到触发兜底的原始异常，
 * 可以在此打印 ERROR 日志（含堆栈），便于定位"admin 宕机 / 超时 / 业务异常 / 熔断"等不同失败原因，
 * 而最终返回给前端的降级响应仍由统一的 Fallback 实例负责，保持响应体规范一致。
 *
 * <p>本类未显式指定 {@code @FeignFallback(SysUserFeignClient.class)}，由 Registrar 从
 * {@code implements FallbackFactory<SysUserFeignClient>} 的泛型参数自动推断兜底目标并强校验。
 *
 * <p><b>日志约定</b>：
 * <ul>
 *   <li>ERROR 级别打印完整堆栈：保证告警链路（日志采集 + 监控告警）能捕获到"触发 fallback"事件</li>
 *   <li>附带目标 Feign 客户端、失败原因 message：无需翻堆栈首行即可快速判断是超时、拒绝连接还是熔断</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-11
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
