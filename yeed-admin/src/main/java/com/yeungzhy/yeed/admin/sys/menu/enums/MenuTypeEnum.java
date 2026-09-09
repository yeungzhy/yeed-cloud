package com.yeungzhy.yeed.admin.sys.menu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单类型枚举
 *
 * <p>DB 值域封闭于 {1,2,3}：禁止散落裸数字魔法值（如 {@code menuType == 1}），
 * 一律用枚举或 {@link #parse(Integer)} 转换
 *
 * <p>三层契约由 {@link #code} 一处标注同时成立：
 * <ul>
 *   <li>{@link EnumValue} 与 {@link IEnum#getValue()}：MP 落库与查询，Lambda Wrapper 可直接传枚举</li>
 *   <li>{@link JsonValue}：入参与出参恒为整数 1/2/3，反序列化同样生效，无需再补 {@code @JsonCreator}</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-13
 */
@Getter
@AllArgsConstructor
public enum MenuTypeEnum implements IEnum<Integer> {

    /** 目录（1）：侧边栏分组，本身不承载页面 */
    DIRECTORY(1, "目录"),

    /** 菜单（2）：页面入口，对应前端路由 */
    MENU_PAGE(2, "菜单"),

    /** 按钮（3）：纯权限点，不显示在侧边栏 */
    BUTTON(3, "按钮"),

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
     * @param code 数据库存储值（1/2/3）
     * @return 对应枚举；入参为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static MenuTypeEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 1 -> DIRECTORY;
            case 2 -> MENU_PAGE;
            case 3 -> BUTTON;
            default -> throw new IllegalArgumentException("未知的 MenuTypeEnum code: " + code);
        };
    }

}
