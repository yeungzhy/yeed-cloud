package com.yeungzhy.yeed.common.core.config;

import com.yeungzhy.yeed.common.core.security.DefaultLoginUserContext;
import com.yeungzhy.yeed.common.core.security.LoginUserContext;
import com.yeungzhy.yeed.common.core.security.LoginUserContextBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 登录用户上下文自动装配
 *
 * <p>注册 {@link LoginUserContext} 的兜底实现 {@link DefaultLoginUserContext} 与
 * {@link LoginUserContextBinder}：
 * <ul>
 *   <li>{@code defaultLoginUserContext}：{@code @ConditionalOnMissingBean} 兜底，引入 common-security 后
 *       SaTokenLoginUserContext 先注册，本 Bean 自动让位；</li>
 *   <li>{@code loginUserContextBinder}：无论哪种实现生效，统一绑定到 {@link LoginUserHolder} 静态字段，
 *       业务代码通过静态方法访问登录信息。</li>
 * </ul>
 *
 * @see com.yeungzhy.yeed.common.core.security.DefaultLoginUserContext
 * @see com.yeungzhy.yeed.common.core.security.LoginUserContextBinder
 */
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
