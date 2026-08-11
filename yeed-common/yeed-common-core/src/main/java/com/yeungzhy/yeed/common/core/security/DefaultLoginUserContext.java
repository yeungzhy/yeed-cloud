package com.yeungzhy.yeed.common.core.security;

public class DefaultLoginUserContext implements LoginUserContext {

    @Override
    public LoginUserVO getLoginUser() {
        // 未引入 common-security 时，所有便捷方法返回 null / empty
        return null;
    }

}
