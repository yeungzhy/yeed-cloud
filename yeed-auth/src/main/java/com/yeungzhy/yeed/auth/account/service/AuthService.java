package com.yeungzhy.yeed.auth.account.service;

import com.yeungzhy.yeed.auth.account.dto.LoginDTO;
import com.yeungzhy.yeed.auth.account.vo.LoginVO;

/**
 * 认证服务
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
public interface AuthService {

    /**
     * 登录：经 Feign 调 admin 校验凭据，通过后签发 Sa-Token 并写入 session
     *
     * @param dto 账号 + 密码
     * @return 登录令牌 + 登录身份包
     * @author yeungzhy
     * @since 2026-08-09
     */
    LoginVO login(LoginDTO dto);


}
