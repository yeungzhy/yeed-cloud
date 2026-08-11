package com.yeungzhy.yeed.common.core.security;

import org.springframework.beans.factory.InitializingBean;

// common-core: com.yeungzhy.yeed.common.security.LoginUserContextBinder
// Spring 容器初始化时把 LoginUserContext Bean 绑定到 LoginUserHolder 静态字段
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