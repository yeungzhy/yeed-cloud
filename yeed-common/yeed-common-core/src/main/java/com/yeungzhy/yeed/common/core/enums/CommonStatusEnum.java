package com.yeungzhy.yeed.common.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用"启用/禁用"状态枚举（全系统唯一真相源）
 * <p>所有业务实体的 {@code status} 字段统一使用本枚举承载语义：
 * <ul>
 *   <li>DB 层：通过 {@link #code}（0/1）与数据库 TINYINT/INT 列互转（{@link EnumValue} 标注）</li>
 *   <li>ORM 层：MyBatis-Plus 识别 {@link IEnum} / {@link EnumValue}，Lambda Wrapper 直接用枚举即可</li>
 *   <li>序列化层：Jackson 用 {@link JsonValue} 返回整数 0/1 给前端（保持接口契约不变）</li>
 * </ul>
 * <p>禁止在任何业务代码中散落裸数字 0/1 判断"启用/禁用"语义，
 * 一律使用 {@link #ENABLED} / {@link #DISABLED} 枚举或 {@link #fromCode(Integer)} 做转换。
 *
 * <p><b>模块归属说明</b>：本枚举位于 {@code yeed-common-core} 而非 {@code yeed-common-data}。
 * 原因："启用/禁用"是系统级通用语义契约，应与 {@link SysRoleEnum} 同级、可被所有 common
 * 子模块（cache/security/web/data）及业务模块自由引用；若放在 data 模块，则任何引用方
 * 都会被迫拖入 mybatis-plus + druid + mysql 驱动等持久化全家桶，造成依赖污染。
 * 本模块通过仅引入 {@code mybatis-plus-annotation}（纯注解包，零传递依赖）和
 * {@code jackson-annotations}（provided）承载 ORM/序列化注解，既保留双保险机制，
 * 又不破坏 core 的轻量定位。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Getter
@AllArgsConstructor
public enum CommonStatusEnum implements IEnum<Integer> {

    /** 禁用（0） */
    DISABLED(0, "禁用"),

    /** 启用（1） */
    ENABLED(1, "启用"),

    ;

    /**
     * 数据库存储值（0-禁用，1-启用）
     * <p>MP {@link EnumValue} + {@link IEnum#getValue()} 双保险，
     * 兼容 MP 3.5.x 的两种枚举识别机制。
     */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述（用于日志/字典渲染） */
    private final String desc;

    /**
     * 实现 {@link IEnum#getValue()}，返回 MP 写入数据库的值
     */
    @Override
    public Integer getValue() {
        return this.code;
    }

    /**
     * 根据数据库值反查枚举（DTO(Integer) → Entity(Enum) 转换使用）
     * <p>约定：{@code null} 入参返回 {@code null}，便于"前端不传就不修改"语义；
     * 非法值抛出 {@link IllegalArgumentException}，及早暴露脏数据/错误调用。
     *
     * @param code 数据库存储值（0/1）
     * @return 对应枚举或 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static CommonStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        // 固定两个分支时用 switch 比 for 循环 + values() clone 更省；
        // 编译器会把 case 0/1 编译为 tableswitch（O(1)跳转），零额外堆内存。
        return switch (code) {
            case 0 -> DISABLED;
            case 1 -> ENABLED;
            default -> throw new IllegalArgumentException("未知的 CommonStatusEnum code: " + code);
        };
    }

    /**
     * 是否"启用"状态（空值视为非启用，便于防御式判断）
     */
    public static boolean isEnabled(CommonStatusEnum status) {
        return status == ENABLED;
    }

    /**
     * 是否"禁用"状态（空值视为非禁用，便于防御式判断）
     */
    public static boolean isDisabled(CommonStatusEnum status) {
        return status == DISABLED;
    }

}
