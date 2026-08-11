package com.yeungzhy.yeed.common.core.security;

/**
 * Sa-Token 会话 session key 常量
 *
 * <p> 下游服务写入方与读取方统一引用本常量，避免散落魔法字符串
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
public final class SessionKeys {
    private SessionKeys() {}

    /** session 中存放登录身份包（{@link LoginUserVO}）的 key */
    public static final String LOGIN_USER = "yeed:loginUser";

}
