package com.yeungzhy.yeed.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统内置角色编码常量（与数据库默认角色一一对应）
 *
 * <p>跨服务共享的角色语义契约：权限短路判断、默认数据初始化、异步任务（Job/MQ）等
 * 硬编码逻辑统一引用本枚举，禁止散落魔法字符串。
 * <p>注意：本枚举仅承载代码需要分支判断的<b>内置角色</b>；运行时动态的角色数据
 * （角色记录、用户-角色关联）仍须通过内部 Feign 向数据持有服务获取，不走本枚举。
 * 新增内置角色时，需同步维护引用 {@code values()} 做默认角色种子的初始化逻辑。
 *
 * @author yeungzhy
 * @since 2026-08-08
 */
@Getter
@AllArgsConstructor
public enum SysRoleEnum {

    /** 超级管理员 */
    SUPER_ADMIN("超级管理员", "SUPER_ADMIN", "拥有系统全部权限"),

    /** 普通用户 */
    USER("普通用户", "USER", "系统默认注册角色"),


    ;
    private final String roleName;
    private final String roleCode;
    private final String description;

}
