package com.yeungzhy.yeed.api.user.feign;

import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.fallback.SysUserFeignClientFallbackFactory;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 系统用户内部 Feign 契约（消费方：yeed-auth；提供方：yeed-admin）
 *
 * <p>admin 在 {@code /internal/user/**} 下暴露内部接口，仅供 auth 经 Feign 调用，
 * 网关层应屏蔽该前缀的外部路由。
 *
 * <p>兜底使用 {@link SysUserFeignClientFallbackFactory} 而非直接 Fallback：
 * Factory 能拿到触发兜底的原始 {@link Throwable}，打印完整 ERROR 日志后再返回降级响应，
 * 兼顾"用户侧不报错 500"与"运维侧可排查"两个目标。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@FeignClient(
        name = "yeed-admin",
        contextId = "sysUserFeignClient",
        path = "/internal/user",
        fallbackFactory = SysUserFeignClientFallbackFactory.class
)
public interface SysUserFeignClient {

    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包（用户信息 + 角色编码 + 权限标识）
     *
     * @param dto 账号 + 密码
     * @return 登录身份包；账号不存在或密码错误时由 admin 抛业务异常（ApiResult.error）
     */
    @PostMapping("/verify")
    ApiResult<LoginUserInfo> verify(@RequestBody UserVerifyDTO dto);


}
