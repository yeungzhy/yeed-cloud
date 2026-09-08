package com.yeungzhy.yeed.common.core.config;

import com.yeungzhy.yeed.common.core.security.DefaultLoginUserProvider;
import com.yeungzhy.yeed.common.core.security.LoginUserProvider;
import com.yeungzhy.yeed.common.core.security.LoginUserProviderRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 登录用户上下文自动装配
 *
 * <p> 注册 {@link LoginUserProvider} 的兜底实现 {@link DefaultLoginUserProvider} 与
 * {@link LoginUserProviderRegistrar}
 * <ul>
 *   <li>{@code defaultLoginUserProvider}：{@code @ConditionalOnMissingBean} 兜底，引入 common-security 后
 *       SaTokenLoginUserProvider 先注册，本 Bean 自动让位
 *   <li>{@code loginUserProviderRegistrar}：无论哪种实现生效，统一绑定到
 *       {@link com.yeungzhy.yeed.common.core.security.LoginUserHelper} 静态字段，业务代码通过静态方法访问
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-10
 * @see DefaultLoginUserProvider
 * @see LoginUserProviderRegistrar
 */
@AutoConfiguration
public class LoginUserProviderAutoConfiguration {

    /**
     * 兜底实现：仅当容器中没有其他 {@link LoginUserProvider} Bean 时注册
     * <p> 引入 common-security 后，SaTokenLoginUserProvider 会先注册，此 Bean 因 @ConditionalOnMissingBean 跳过
     */
    @Bean
    @ConditionalOnMissingBean(LoginUserProvider.class)
    public LoginUserProvider defaultLoginUserProvider() {
        return new DefaultLoginUserProvider();
    }

    /**
     * 把生效中的 {@link LoginUserProvider} 绑定到 {@link LoginUserHelper} 静态字段
     * <p> 不管是默认实现还是 sa-token 实现，统一由 Registrar 绑定
     */
    @Bean
    public LoginUserProviderRegistrar loginUserProviderRegistrar(LoginUserProvider context) {
        return new LoginUserProviderRegistrar(context);
    }

}
