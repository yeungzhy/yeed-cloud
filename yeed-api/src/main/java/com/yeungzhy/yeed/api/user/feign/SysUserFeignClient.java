package com.yeungzhy.yeed.api.user.feign;

import com.yeungzhy.yeed.api.user.dto.UserMenuDTO;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.api.user.feign.fallback.SysUserFeignClientFallbackFactory;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.security.MenuTreeInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 系统用户内部 Feign 契约（消费方：yeed-auth；提供方：yeed-admin）
 *
 * <p>admin 在 {@code /internal/user/**} 下暴露内部接口，仅供 auth 经 Feign 调用，
 * 网关层应屏蔽该前缀的外部路由，避免内部契约泄漏。
 *
 * <p>兜底走 {@link SysUserFeignClientFallbackFactory}（FallbackFactory 形态），
 * 触发时可记录原始 {@link Throwable}；降级响应契约见 {@link SysUserFeignClientFallback}。
 *
 * @author yeungzhy
 * @since 2026-08-09
 * @see SysUserFeignClientFallbackFactory
 * @see SysUserFeignClientFallback
 */
@FeignClient(
        name = "yeed-admin",
        contextId = "sysUserFeignClient",
        path = "/internal/user",
        fallbackFactory = SysUserFeignClientFallbackFactory.class
)
public interface SysUserFeignClient {

    /**
     * 校验账号密码，通过则返回登录身份包（用户信息 + 角色编码 + 权限标识）。
     *
     * @param dto 账号 + 密码
     * @return 登录身份包；账号不存在或密码错误属业务失败，由 admin 返回 {@code ApiResult.error}
     *         走正常响应链路，不触发兜底
     */
    @PostMapping("/verify")
    ApiResult<LoginUserInfo> verify(@RequestBody UserVerifyDTO dto);

    /**
     * 查询用户可见菜单树，用于登录响应与前端侧边栏渲染。
     *
     * @param dto 用户 ID
     * @return 已建树的菜单树节点；远程调用失败时由兜底返回降级错误
     */
    @PostMapping("/user-menus")
    ApiResult<List<MenuTreeInfo>> userMenus(@RequestBody UserMenuDTO dto);

}
