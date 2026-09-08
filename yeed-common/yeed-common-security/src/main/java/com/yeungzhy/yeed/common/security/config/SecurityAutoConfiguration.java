package com.yeungzhy.yeed.common.security.config;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.config.LoginUserProviderAutoConfiguration;
import com.yeungzhy.yeed.common.core.security.LoginUserProvider;
import com.yeungzhy.yeed.common.security.SaTokenLoginUserProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Sa-Token 登录用户上下文自动配置。
 *
 * <p>当 classpath 存在 Sa-Token（{@code StpUtil}）时，注册基于 Sa-Token 会话的
 * {@link SaTokenLoginUserProvider} 作为 {@link LoginUserProvider} 实现，供微服务统一获取当前登录用户。
 *
 * <p>装配约定：
 * <ul>
 *   <li>{@link AutoConfigureBefore}：抢在 core 的默认 {@link LoginUserProviderAutoConfiguration} 之前装配，
 *       使 Sa-Token 成为默认取登录用户的方式</li>
 *   <li>{@link ConditionalOnMissingBean}：业务方自定义了 {@link LoginUserProvider} 时自动失效，不重复装配</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see SaTokenLoginUserProvider
 * @see LoginUserProvider
 */
@AutoConfiguration
@AutoConfigureBefore(LoginUserProviderAutoConfiguration.class)
@ConditionalOnClass(StpUtil.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoginUserProvider.class)
    public LoginUserProvider saTokenLoginUserContext(ObjectMapper objectMapper) {
        return new SaTokenLoginUserProvider(objectMapper);
    }

    @Bean
    public StpLogic getStpLogicJwt() {
        // 仅凭 Token 字符串取用户基本信息、完全不查 Redis 的场景（如日志审计）可改用 StpLogicJwtForMixin
        return new StpLogicJwtForSimple();
    }

}
