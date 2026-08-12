package com.yeungzhy.yeed.common.core.security;

import com.yeungzhy.yeed.common.core.config.LoginUserProviderAutoConfiguration;

/**
 * {@link LoginUserProvider} 兜底实现（未引入 common-security 时的默认行为）
 *
 * <p>所有便捷方法返回 null / 空集合，避免业务代码因缺少登录上下文直接 NPE；
 * 由 {@link LoginUserProviderAutoConfiguration} 注册，
 * 引入 common-security 后因 {@code @ConditionalOnMissingBean} 自动失效。
 */
public class DefaultLoginUserProvider implements LoginUserProvider {

    @Override
    public LoginUserInfo getLoginUser() {
        // 未引入 common-security 时，所有便捷方法返回 null / empty
        return null;
    }

}
