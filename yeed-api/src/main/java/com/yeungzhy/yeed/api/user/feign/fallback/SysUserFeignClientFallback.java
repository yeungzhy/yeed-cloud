package com.yeungzhy.yeed.api.user.feign.fallback;

import com.yeungzhy.yeed.api.feign.FeignFallback;
import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.SysUserFeignClient;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;

import java.util.List;

/**
 * {@link SysUserFeignClient} 的兜底降级实现：admin 不可达、调用超时或抛出未处理异常时，
 * 由 Spring Cloud CircuitBreaker（Resilience4j）触发，返回统一降级错误，
 * 避免异常冒泡到 auth 业务层。
 *
 * <p>仅远程调用失败进入兜底；业务失败（账号不存在、密码错误）由 admin 正常返回
 * {@code ApiResult.error} 走响应链路，不经过本类。
 *
 * <p>未显式指定 {@code @FeignFallback(SysUserFeignClient.class)}，兜底目标由 Registrar 从
 * {@code implements SysUserFeignClient} 自动推断；本类经
 * {@link com.yeungzhy.yeed.api.feign.EnableFeignFallbacks} 扫描注册为 Bean，不标注 {@code @Component}。
 *
 * @author yeungzhy
 * @since 2026-08-09
 * @see SysUserFeignClientFallbackFactory
 */
public class SysUserFeignClientFallback implements SysUserFeignClient {

    @Override
    public ApiResult<LoginUserInfo> verify(UserVerifyDTO dto) {
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

    @Override
    public ApiResult<List<MenuTreeInfo>> userMenus(UserMenuDTO dto) {
        return ApiResult.error(ApiResult.CommonCode.REMOTE_SERVICE_ERROR);
    }

}
