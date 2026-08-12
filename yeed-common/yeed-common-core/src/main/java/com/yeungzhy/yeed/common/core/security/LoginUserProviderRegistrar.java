package com.yeungzhy.yeed.common.core.security;

import com.yeungzhy.yeed.common.core.config.LoginUserProviderAutoConfiguration;
import org.springframework.beans.factory.InitializingBean;

/**
 * LoginUserContext 绑定器
 *
 * <p>由 {@link LoginUserProviderAutoConfiguration} 注册，
 * 在容器初始化阶段（{@link InitializingBean#afterPropertiesSet()}）把生效的
 * {@link LoginUserProvider} Bean 绑定到 {@link LoginUserHelper} 静态字段，
 * 业务代码即可静态调用获取登录信息。
 */
public class LoginUserProviderRegistrar implements InitializingBean {

    private final LoginUserProvider context;

    public LoginUserProviderRegistrar(LoginUserProvider context) {
        this.context = context;
    }

    @Override
    public void afterPropertiesSet() {
        LoginUserHelper.bind(context);
    }
}