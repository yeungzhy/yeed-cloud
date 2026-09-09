package com.yeungzhy.yeed.common.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 启用状态枚举（全系统 status 字段的唯一真相源）
 *
 * <p>禁止在业务代码散落裸数字 0/1 判断启用与否，一律用 {@link #ENABLED} / {@link #DISABLED}
 * 或 {@link #parse(Integer)} 转换
 *
 * <p>三层契约由 {@link #code} 一处标注同时成立：
 * <ul>
 *   <li>{@link EnumValue} 与 {@link IEnum#getValue()}：MP 落库与查询，Lambda Wrapper 可直接传枚举</li>
 *   <li>{@link JsonValue}：入参与出参恒为整数 0/1，反序列化同样生效，无需再补 {@code @JsonCreator}</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Getter
@AllArgsConstructor
public enum EnableStatusEnum implements IEnum<Integer> {

    /** 禁用（0） */
    DISABLED(0, "禁用"),

    /** 启用（1） */
    ENABLED(1, "启用"),

    ;

    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述（用于日志/字典渲染） */
    private final String desc;

    @Override
    public Integer getValue() {
        return this.code;
    }

    /**
     * 解析数据库值（Integer → 枚举）
     *
     * <p>封闭域语义：{@code null} 返回 {@code null}（支持"前端不传就不修改"），范围外取值视为脏数据 fail-fast
     *
     * @param code 数据库存储值（0/1）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static EnableStatusEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        /*
         * 两个分支走 switch：编译为 tableswitch（O(1) 跳转），且不 clone values() 数组
         * String switch 先按哈希分派，不适用此结论
         */
        return switch (code) {
            case 0 -> DISABLED;
            case 1 -> ENABLED;
            default -> throw new IllegalArgumentException("未知的 EnableStatusEnum code: " + code);
        };
    }

    /** 是否"启用"状态（空值视为非启用，便于防御式判断） */
    public static boolean isEnabled(EnableStatusEnum status) {
        return status == ENABLED;
    }

    /** 是否"禁用"状态（空值视为非禁用，便于防御式判断） */
    public static boolean isDisabled(EnableStatusEnum status) {
        return status == DISABLED;
    }

}
