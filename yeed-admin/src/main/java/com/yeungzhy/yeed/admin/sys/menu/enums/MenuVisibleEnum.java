package com.yeungzhy.yeed.admin.sys.menu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单可见性枚举（是否显示在侧边栏）
 * <p>DB 值域固定 {0,1}，禁止散落裸数字 0/1 判断"隐藏/显示"语义，
 * 一律使用 {@link #HIDDEN} / {@link #SHOWN} 枚举或 {@link #parse(Integer)} 做转换：
 * <ul>
 *   <li>DB 层：通过 {@link #code} 与 TINYINT/INT 列互转（{@link EnumValue} 标注）</li>
 *   <li>ORM 层：MyBatis-Plus 识别 {@link IEnum}，Lambda Wrapper 直接用枚举</li>
 *   <li>序列化层：Jackson 的 {@link JsonValue} 双向生效，接口契约恒为整数 0/1</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Getter
@AllArgsConstructor
public enum MenuVisibleEnum implements IEnum<Integer> {

    /** 隐藏（0）：不显示在侧边栏 */
    HIDDEN(0, "隐藏"),

    /** 显示（1）：显示在侧边栏 */
    SHOWN(1, "显示"),

    ;

    /**
     * 数据库存储值（0-隐藏，1-显示）
     * <p>MP {@link EnumValue} + {@link IEnum#getValue()} 双保险，
     * 兼容 MP 3.5.x 的两种枚举识别机制
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
     * 解析数据库值（DTO/前端入参的 Integer → 枚举）
     * <p>封闭域解析语义：{@code null} 入参返回 {@code null}（便于"前端不传就不修改"），
     * 范围外取值视为脏数据，抛 {@link IllegalArgumentException} 直接暴露
     *
     * @param code 数据库存储值（0/1）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static MenuVisibleEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> HIDDEN;
            case 1 -> SHOWN;
            default -> throw new IllegalArgumentException("未知的 MenuVisibleEnum code: " + code);
        };
    }

}
