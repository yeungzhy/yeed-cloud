package com.yeungzhy.yeed.common.security;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.security.LoginUserContext;
import com.yeungzhy.yeed.common.core.security.LoginUserContextAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

// common-security: SecurityAutoConfiguration
@AutoConfiguration
@AutoConfigureBefore(LoginUserContextAutoConfiguration.class)
@ConditionalOnClass(StpUtil.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoginUserContext.class)
    public LoginUserContext saTokenLoginUserContext(ObjectMapper objectMapper) {
        return new SaTokenLoginUserContext(objectMapper);
    }
}