package com.yeungzhy.yeed.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内置角色编码常量（跨服务共享的角色语义契约）
 *
 * <p>权限短路判断、默认数据初始化、异步任务（Job/MQ）等硬编码逻辑统一引用本枚举，
 * 禁止散落魔法字符串。
 *
 * <p><b>准入标准</b>：仅"代码需要分支判断"的角色进本枚举（Builtin，随版本固化）；
 * 运行时动态的角色数据（角色记录、用户-角色关联）仍须通过内部 Feign 向数据持有服务获取，
 * 不走本枚举。
 *
 * <p><b>角色层级说明</b>：
 * <ul>
 *   <li>{@link #SUPER_ADMIN}：代码级短路身份，权限校验直接放行，不依赖数据库
 *       {@code perms} 配置——权限配置被改坏时仍可登录修复系统（逃生通道），不可被禁用；</li>
 *   <li>{@link #MEMBER}：默认注册角色，拥有基础权限；</li>
 *   <li>「拥有全部业务权限的管理员」不在此列：以数据库种子角色维护（{@code sys_role}
 *       表配齐 perms）即可，待出现两级管理（管理员互管）或数据权限边界需求时再升格为内置。</li>
 * </ul>
 *
 * <p>新增内置角色时，需同步维护引用 {@code values()} 做默认角色种子的初始化逻辑。
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
    private final String roleName;
    private final String roleCode;
    private final String description;

}
