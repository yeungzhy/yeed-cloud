package com.yeungzhy.yeed.common.core.security;

/**
 * 唯一抽象接口, 获取当前登录人
 *
 * @author yeungzhy
 * @since 2026-08-10 21:21
 */
public interface LoginUserContext {

    /**
     * 获取当前登录用户完整身份信息
     * <p> 这是唯一抽象方法，其他信息（userId/username/roles 等）都从 LoginUserVO 派生
     *
     * @return 登录身份包；未登录或无法获取时返回 null
     */
    LoginUserVO getLoginUser();

}
