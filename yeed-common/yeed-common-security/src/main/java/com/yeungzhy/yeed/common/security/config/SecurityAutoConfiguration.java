package com.yeungzhy.yeed.common.security.config;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.config.LoginUserContextAutoConfiguration;
import com.yeungzhy.yeed.common.core.security.LoginUserContext;
import com.yeungzhy.yeed.common.security.SaTokenLoginUserContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
