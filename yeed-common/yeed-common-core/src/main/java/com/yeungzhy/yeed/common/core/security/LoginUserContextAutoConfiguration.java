package com.yeungzhy.yeed.common.core.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

// common-core: com.yeungzhy.yeed.common.security.LoginUserContextAutoConfiguration
@AutoConfiguration
public class LoginUserContextAutoConfiguration {

    /**
     * 兜底实现：仅当容器中没有其他 LoginUserContext Bean 时注册
     * <p> 如果引入了 common-security，SaTokenLoginUserContext 会先注册，
     * 此 Bean 因 @ConditionalOnMissingBean 跳过
     */
    @Bean
    @ConditionalOnMissingBean(LoginUserContext.class)
    public LoginUserContext defaultLoginUserContext() {
        return new DefaultLoginUserContext();
    }

    /**
     * 不管是默认实现还是 sa-token 实现，统一由 Binder 绑定到静态字段
     */
    @Bean
    public LoginUserContextBinder loginUserContextBinder(LoginUserContext context) {
        return new LoginUserContextBinder(context);
    }
}