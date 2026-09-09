package com.yeungzhy.yeed.admin.sys.menu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单可见性枚举（是否显示在侧边栏）
 *
 * <p>DB 值域封闭于 {0,1}：禁止散落裸数字 0/1 判断显示与否，
 * 一律用 {@link #HIDDEN} / {@link #SHOWN} 或 {@link #parse(Integer)} 转换
 *
 * <p>三层契约由 {@link #code} 一处标注同时成立：
 * <ul>
 *   <li>{@link EnumValue} 与 {@link IEnum#getValue()}：MP 落库与查询，Lambda Wrapper 可直接传枚举</li>
 *   <li>{@link JsonValue}：入参与出参恒为整数 0/1，反序列化同样生效，无需再补 {@code @JsonCreator}</li>
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
