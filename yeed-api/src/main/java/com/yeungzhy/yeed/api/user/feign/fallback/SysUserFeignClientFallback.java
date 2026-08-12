package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.feign.FeignFallback;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;

/**
 * {@link SysUserFeignClient} 兜底降级实现。
 *
 * <p>当 yeed-admin 不可达、调用超时或抛出未处理异常时，Spring Cloud CircuitBreaker（Resilience4j）
 * 触发本兜底实现，避免异常冒泡到 auth 业务层，让登录流程返回友好降级提示而非 500。
 *
 * <p><b>触发边界</b>：仅远程调用失败（网络异常、超时、admin 宕机等）时触发。
 * <b>不</b>覆盖业务失败——账号不存在、密码错误由 admin 正常返回 {@code ApiResult.error}，
 * 走正常响应链路，不会进入兜底。
 *
 * <p>本类未显式指定 {@code @FeignFallback(SysUserFeignClient.class)}，由 Registrar 从
 * {@code implements SysUserFeignClient} 自动推断兜底目标并强校验契约实现。
 *
 * <p>本类不标注 {@code @Component}，而是通过 {@link com.yeungzhy.yeed.api.feign.EnableFeignFallbacks}
 * 扫描 {@link FeignFallback} 注解自动注册为 Bean，避免触发包扫描，零副作用。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
public class SysUserFeignClientFallback implements SysUserFeignClient {

    @Override
    public ApiResult<LoginUserInfo> verify(UserVerifyDTO dto) {
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

}
