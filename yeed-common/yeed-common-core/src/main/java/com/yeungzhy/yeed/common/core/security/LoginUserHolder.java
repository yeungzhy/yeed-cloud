package com.yeungzhy.yeed.common.core.security;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户信息静态访问入口
 *
 * <p>由 {@link LoginUserContextBinder} 在容器启动时绑定生效的 {@link LoginUserContext}，
 * 业务代码无需注入 Bean，直接静态调用本类方法即可获取当前登录用户信息。
 *
 * <p>未绑定上下文（common-core 单独使用）或未登录时，派生方法统一返回 null / 空集合，不抛异常；
 * 需要强制登录的场景使用 {@link #requireUserId()}。
 *
 * @see LoginUserContextBinder
 * @see LoginUserContext
 */
public final class LoginUserHolder {

    private static volatile LoginUserContext context;

    private LoginUserHolder() {}

    /** 由 LoginUserContextBinder 在容器启动时调用（package-private） */
    static void bind(LoginUserContext context) {
        LoginUserHolder.context = context;
    }

    // ==================== 核心方法 ====================

    /** 是否已登录 */
    public static boolean isLogin() {
        return getLoginUser() != null;
    }

    /** 获取完整登录身份包 */
    public static LoginUserVO getLoginUser() {
        return context != null ? context.getLoginUser() : null;
    }

    // ==================== 派生便捷方法 ====================
    // 每个方法内部只调一次 getLoginUser()，避免重复读取

    public static Long getUserId() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取当前登录用户 ID，未登录时返回 defaultValue
     * <p>用于 AutoFillFieldHandler 等非登录场景（如定时任务批量插入）的兜底填充，
     * 行为与 StpUtil.getLoginId(defaultValue) 一致——不抛异常，直接返回兜底值
     *
     * @param defaultValue 未登录时的兜底值
     * @return 登录用户 ID，或 defaultValue
     */
    public static Long getUserId(Long defaultValue) {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getUserId() : defaultValue;
    }

    /**
     * 获取当前登录用户 ID，未登录时抛异常
     * <p>行为与 StpUtil.getLoginIdAsLong() 一致——未登录直接抛异常，不返回 null
     * <p>用于 BaseMapper.deleteByIdAutoFill 等必须登录的场景；
     * 非登录场景（定时任务等）请使用 {@link #getUserId(Long)} 传兜底值，
     * 或绕过 AutoFill 方法显式传 deleteBy
     *
     * @return 登录用户 ID
     * @throws IllegalStateException 未登录或登录上下文不可用时抛出
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("无法获取当前登录用户 ID：未登录或登录上下文不可用");
        }
        return userId;
    }

    public static String getUsername() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getUsername() : null;
    }

    public static String getRealName() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getRealName() : null;
    }

    public static String getEmployeeNo() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getEmployeeNo() : null;
    }

    public static List<String> getRoleCodes() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getRoleCodes() : Collections.emptyList();
    }

    public static List<String> getPerms() {
        LoginUserVO user = getLoginUser();
        return user != null ? user.getPerms() : Collections.emptyList();
    }

    // ==================== 判断类便捷方法 ====================

    /** 是否拥有指定角色 */
    public static boolean hasRole(String roleCode) {
        List<String> roles = getRoleCodes();
        return roles.contains(roleCode);
    }

    /** 是否拥有指定权限标识 */
    public static boolean hasPermission(String perm) {
        List<String> perms = getPerms();
        return perms.contains(perm);
    }

    /** 是否拥有任意一个指定角色（OR 语义） */
    public static boolean hasAnyRole(String... roleCodes) {
        List<String> roles = getRoleCodes();
        for (String code : roleCodes) {
            if (roles.contains(code)) return true;
        }
        return false;
    }

    /** 是否拥有任意一个指定权限（OR 语义） */
    public static boolean hasAnyPermission(String... perms) {
        List<String> userPerms = getPerms();
        for (String perm : perms) {
            if (userPerms.contains(perm)) return true;
        }
        return false;
    }

}