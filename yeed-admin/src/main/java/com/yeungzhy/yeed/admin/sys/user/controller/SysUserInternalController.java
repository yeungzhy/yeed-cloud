package com.yeungzhy.yeed.admin.sys.user.controller;

import com.yeungzhy.yeed.admin.sys.user.service.SysUserService;
import com.yeungzhy.yeed.api.user.dto.UserVerifyDTO;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户 内部接口控制器（仅供 auth 经 Feign 调用）
 *
 * <p>挂载于 {@code /internal/**} 前缀：
 * <ul>
 *   <li>网关层应未定义该前缀的外部路由，避免凭据校验接口被外部直调；</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/internal/user")
public class SysUserInternalController {

    @Resource
    private SysUserService sysUserService;


    /**
     * 凭据校验：校验账号密码，校验通过返回登录身份包
     *
     * @param dto 账号 + 密码
     * @return 登录身份包（用户信息 + 角色编码 + 权限标识 + 菜单树）
     */
    @PostMapping("/verify")
    public ApiResult<LoginUserInfo> verify(@Valid @RequestBody UserVerifyDTO dto) {
        return ApiResult.ok(sysUserService.verify(dto));
    }

}
