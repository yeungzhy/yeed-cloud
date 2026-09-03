package com.yeungzhy.yeed.common.core.security;

import com.yeungzhy.yeed.common.core.enums.BuiltinRoleEnum;

import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户静态访问门面
 *
 * <p>由 {@link LoginUserProviderRegistrar} 在容器启动时绑定生效的 {@link LoginUserProvider}，
 * 业务代码无需注入 Bean，直接静态调用本类方法即可获取当前登录用户信息。
 *
 * <p><b>语义按"返回值类别"分族，调用点无需逐个权衡强弱语义</b>：
 * <ul>
 *   <li><b>值族</b>（{@link #getUserId()} / {@link #getUsername()} / {@link #getRealName()} /
 *       {@link #getEmployeeNo()}）：取"操作主体"，<b>默认强登录语义</b>——未登录直接抛
 *       {@link IllegalStateException}，不返回 null。理由：业务代码运行在网关统一鉴权之后，
 *       （除放行名单外）匿名请求到不了这里；取不到人意味着代码跑在非请求线程（定时任务/异步执行器）
 *       或身份基础设施未装配，属编程错误——返回 null 只会让错误向下游扩散（拿 null 拼条件、落库），
 *       就地 fail-fast 才是最小代价。语义等价 {@code StpUtil.getLoginIdAsLong()}。</li>
 *   <li><b>谓词族</b>（{@link #isLogin()} / {@link #hasRole(String)} / {@link #isSuperAdmin()} /
 *       {@link #getRoleCodes()} / {@link #getPerms()}）：问身份与权限，<b>空安全</b>——
 *       未登录返回 false / 空集合。匿名请求做权限判断应得到安全答案，false 不会像 null 那样扩散。</li>
 *   <li><b>逃生通道</b>（{@link #getLoginUser()} / {@link #getUserId(Long)}）：面向框架与系统代码
 *       （审计字段自动填充、匿名身份捕获、定时任务），场景本身就容忍"没有登录人"，
 *       由调用方通过 nullable 根方法或<b>显式兜底值</b>表达该意图。</li>
 * </ul>
 *
 * @see LoginUserProviderRegistrar
 * @see LoginUserProvider
 */
public final class LoginUserHelper {

    private static volatile LoginUserProvider context;

    private LoginUserHelper() {}

    /** 由 LoginUserProviderRegistrar 在容器启动时调用（package-private） */
    static void bind(LoginUserProvider context) {
        LoginUserHelper.context = context;
    }

    // ==================== 核心方法 ====================

    /** 是否已登录：未绑定上下文或未登录时返回 false */
    public static boolean isLogin() {
        return getLoginUser() != null;
    }

    /**
     * 获取完整登录身份包（nullable 根方法，逃生通道）
     *
     * <p>本类唯一会返回 null 的取值入口：框架/系统代码需要"可能没有登录人"语义时使用
     * （如审计字段自动填充、匿名请求身份捕获）；业务代码请用强语义的值族方法。
     *
     * @return 登录身份包；未绑定上下文（common-core 单独使用）或未登录时返回 null
     */
    public static LoginUserInfo getLoginUser() {
        return context != null ? context.getLoginUser() : null;
    }

    // ==================== 值族：默认强登录语义 ====================

    /**
     * 获取当前登录用户 ID
     *
     * @return 登录用户 ID（非空）
     * @throws IllegalStateException 未登录、或代码运行在无登录上下文的线程（定时任务/异步执行器）时抛出
     */
    public static Long getUserId() {
        return requireLoginUser().getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录时返回 defaultValue（逃生通道）
     *
     * <p>用于审计字段自动填充等必须容忍"非用户操作"的场景：{@code getUserId(null)} 表达
     * "无登录上下文就填 null"，而非让无登录态成为程序错误。
     *
     * @param defaultValue 未登录时的兜底值
     * @return 登录用户 ID，或 defaultValue
     */
    public static Long getUserId(Long defaultValue) {
        LoginUserInfo user = getLoginUser();
        return user != null ? user.getUserId() : defaultValue;
    }

    /**
     * 获取登录账号名
     *
     * @return 登录账号名（非空）
     * @throws IllegalStateException 未登录或登录上下文不可用时抛出
     */
    public static String getUsername() {
        return requireLoginUser().getUsername();
    }

    /**
     * 获取真实姓名
     *
     * @return 真实姓名（非空）
     * @throws IllegalStateException 未登录或登录上下文不可用时抛出
     */
    public static String getRealName() {
        return requireLoginUser().getRealName();
    }

    /**
     * 获取工号
     *
     * @return 工号（非空）
     * @throws IllegalStateException 未登录或登录上下文不可用时抛出
     */
    public static String getEmployeeNo() {
        return requireLoginUser().getEmployeeNo();
    }

    // ==================== 谓词族：空安全 ====================

    /**
     * 获取角色编码集合
     *
     * @return 角色编码集合；未登录时为空集合（恒非 null，供 Sa-Token {@code StpInterface} 直接消费）
     */
    public static List<String> getRoleCodes() {
        LoginUserInfo user = getLoginUser();
        return user != null ? user.getRoleCodes() : Collections.emptyList();
    }

    /**
     * 获取权限标识集合
     *
     * @return 权限标识集合；未登录时为空集合（恒非 null，供 Sa-Token {@code StpInterface} 直接消费）
     */
    public static List<String> getPerms() {
        LoginUserInfo user = getLoginUser();
        return user != null ? user.getPerms() : Collections.emptyList();
    }

    /** 是否拥有指定角色 */
    public static boolean hasRole(String roleCode) {
        return getRoleCodes().contains(roleCode);
    }

    /** 是否拥有指定权限标识 */
    public static boolean hasPermission(String perm) {
        return getPerms().contains(perm);
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

    /**
     * 当前登录用户是否为超级管理员
     *
     * @return true-当前用户拥有 SUPER_ADMIN 角色；未登录返回 false
     */
    public static boolean isSuperAdmin() {
        return BuiltinRoleEnum.isSuperAdmin(getRoleCodes());
    }

    // ==================== 私有辅助 ====================

    /**
     * 强语义根方法：值族统一入口，未登录直接抛异常
     *
     * <p>异常是"代码在错误环境取身份"的哨兵，不是面向用户的业务错误，故用
     * {@link IllegalStateException} 而非 BizException——它不应被触达。
     */
    private static LoginUserInfo requireLoginUser() {
        LoginUserInfo user = getLoginUser();
        if (user == null) {
            throw new IllegalStateException("无法获取当前登录用户：未登录，或无登录上下文");
        }
        return user;
    }

}
