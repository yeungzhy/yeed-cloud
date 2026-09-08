package com.yeungzhy.yeed.auth.account.service;

import com.yeungzhy.yeed.auth.account.dto.LoginDTO;
import com.yeungzhy.yeed.auth.account.vo.LoginVO;

/**
 * 认证服务
 *
 * <p>凭据校验不在本服务：密码散列只有 admin 持有，这里只认「admin 说通过」
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
public interface AuthService {

    /**
     * 登录：经 Feign 调 admin 校验凭据，通过后签发 Sa-Token 并写入 session
     *
     * @param dto 账号与密码，均不能为空白
     * @return 登录令牌 + 登录身份包 + 菜单树；凭据错误抛 {@code BizException}，不返回失败结果
     * @author yeungzhy
     * @since 2026-08-09
     */
    LoginVO login(LoginDTO dto);


}
