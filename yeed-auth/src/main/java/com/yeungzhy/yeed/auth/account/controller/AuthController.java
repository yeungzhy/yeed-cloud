package com.yeungzhy.yeed.auth.account.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.yeungzhy.yeed.auth.account.dto.LoginDTO;
import com.yeungzhy.yeed.auth.account.service.AuthService;
import com.yeungzhy.yeed.auth.account.vo.LoginVO;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 登录
     *
     * @param dto 账号 + 密码
     * @return 登录令牌 + 登录身份包（角色编码 + 权限标识）
     */
    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResult.ok(authService.login(dto));
    }

    /**
     * 注销
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        log.info("用户注销 loginId={}", loginId);
        return ApiResult.ok();
    }

}
