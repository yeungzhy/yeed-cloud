package com.yeungzhy.yeed.common.core.security;

import org.springframework.beans.factory.InitializingBean;

/**
 * LoginUserContext 绑定器
 *
 * <p>由 {@link com.yeungzhy.yeed.common.core.config.LoginUserContextAutoConfiguration} 注册，
 * 在容器初始化阶段（{@link InitializingBean#afterPropertiesSet()}）把生效的
 * {@link LoginUserContext} Bean 绑定到 {@link LoginUserHolder} 静态字段，
 * 业务代码即可静态调用获取登录信息。
 */
public class LoginUserContextBinder implements InitializingBean {

    private final LoginUserContext context;

    public LoginUserContextBinder(LoginUserContext context) {
        this.context = context;
    }

    @Override
    public void afterPropertiesSet() {
        LoginUserHolder.bind(context);
    }
}