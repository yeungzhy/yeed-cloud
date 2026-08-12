package com.yeungzhy.yeed.common.core.security;

/**
 * 登录用户上下文抽象接口
 *
 * <p>唯一抽象方法为 {@link #getLoginUser()}，其余信息（userId/username/roles 等）均由此派生。
 * 实现类由 {@link LoginUserContextBinder} 绑定到 {@link LoginUserHolder} 静态字段供全局访问。
 *
 * @see com.yeungzhy.yeed.common.core.config.LoginUserContextAutoConfiguration
 * @see DefaultLoginUserContext
 * @author yeungzhy
 * @since 2026-08-10
 */
public interface LoginUserContext {

    /**
     * 获取当前登录用户完整身份信息
     * <p>唯一抽象方法，其他信息（userId/username/roles 等）都从 {@link LoginUserVO} 派生
     *
     * @return 登录身份包；未登录或无法获取时返回 null
     */
    LoginUserVO getLoginUser();

}
