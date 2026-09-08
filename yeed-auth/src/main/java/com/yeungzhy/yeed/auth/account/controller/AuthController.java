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
 * <p>本服务不持有用户数据：凭据校验与菜单树都经 Feign 转调 admin 的内部端点，
 * 这里只负责签发 Sa-Token 与拼装登录响应
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
     * 登录：校验凭据 → 签发 token → 回身份包与菜单树
     *
     * @param dto 账号与密码，均不能为空白；账号可以是登录名或工号
     * @return 登录令牌 + 登录身份包（角色编码 + 权限标识）+ 菜单树；
     *         凭据错误由 admin 抛异常经全局处理透传为业务码
     * @author yeungzhy
     * @since 2026-08-09
     */
    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResult.ok(authService.login(dto));
    }

    /**
     * 注销当前会话
     *
     * <p>只清本 token 的会话，不做"是否已登录"校验：重复注销、拿过期 token 注销都不该报错，
     * token 本身还有没有效由网关鉴权判定
     *
     * @return 固定成功
     * @author yeungzhy
     * @since 2026-08-09
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        log.info("用户注销 loginId={}", loginId);
        return ApiResult.ok();
    }

}
