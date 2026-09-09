package com.yeungzhy.yeed.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内置角色编码常量（跨服务共享的角色语义契约）
 *
 * <p>权限短路判断、默认数据初始化、异步任务等硬编码逻辑统一引用本枚举，禁止散落魔法字符串
 *
 * <p>准入标准：仅"代码需要分支判断"的角色进本枚举（随版本固化），角色记录与用户-角色关联仍须经内部 Feign 获取
 *
 * <p>角色层级：
 * <ul>
 *   <li>{@link #SUPER_ADMIN}：代码级短路身份，权限校验直接放行，不依赖数据库 perms 配置，
 *       即配置被改坏时仍可登录修复（逃生通道），不可被禁用
 *   <li>{@link #MEMBER}：默认注册角色，拥有基础权限
 *   <li>「拥有全部业务权限的管理员」不在此列，以数据库种子角色（{@code sys_role} 配齐 perms）维护
 * </ul>
 *
 * <p>新增内置角色须同步维护引用 {@code values()} 的角色种子初始化逻辑
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
@Getter
@AllArgsConstructor
public enum BuiltinRoleEnum {

    /** 超级管理员：代码级短路身份，校验直接放行全部权限 */
    SUPER_ADMIN("超级管理员", "SUPER_ADMIN", "拥有系统全部权限"),

    /** 普通用户：默认注册角色，拥有基础权限 */
    MEMBER("普通用户", "MEMBER", "系统默认注册角色"),

    ;

    /** 角色中文名 */
    private final String roleName;

    /** 角色编码 */
    private final String roleCode;

    /** 角色说明 */
    private final String description;

    /** 编码 → 枚举反向索引（静态初始化一次，避免每次遍历 values() 克隆数组） */
    private static final Map<String, BuiltinRoleEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(e -> e.roleCode, e -> e));

    /**
     * 查询角色编码是否命中内置白名单（开放域查询语义）
     *
     * <p>roleCode 取值开放，未命中是正常状态而非错误，故返回 {@code null} 由调用方处理
     *
     * @param roleCode 角色编码
     * @return 命中的内置角色枚举；入参为 null 或未命中时返回 null
     */
    public static BuiltinRoleEnum fromCodeOrNull(String roleCode) {
        return roleCode == null ? null : CODE_MAP.get(roleCode);
    }

    /** 判断角色编码是否为内置角色编码 */
    public static boolean isBuiltin(String roleCode) {
        return CODE_MAP.containsKey(roleCode);
    }

    /** 判断角色编码集合中是否含超级管理员 */
    public static boolean isSuperAdmin(Collection<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(SUPER_ADMIN.getRoleCode());
    }

}
