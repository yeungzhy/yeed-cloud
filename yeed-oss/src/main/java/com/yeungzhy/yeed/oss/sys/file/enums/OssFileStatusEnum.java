package com.yeungzhy.yeed.oss.sys.file.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OSS 文件可用状态枚举（文件生命周期领域概念）
 *
 * <p>DB 值域固定 {0,1}（封闭域）：可用是正常态，已清理是过期或主动删除后的终态，生命周期已闭环，
 * 未来引入中间态的概率低；反查 {@link #parse(Integer)} 对范围外取值抛
 * {@link IllegalArgumentException}，直接暴露脏数据
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Getter
@AllArgsConstructor
public enum OssFileStatusEnum implements IEnum<Integer> {

    /** 已清理（0）：对象已删、记录逻辑删除前的标记态 */
    CLEANED(0, "已清理"),

    /** 可用（1）：对象存在、可下载 */
    AVAILABLE(1, "可用"),

    ;

    /** 数据库存储值（0-已清理，1-可用） */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述（用于日志/字典渲染） */
    private final String desc;

    /** 实现 {@link IEnum#getValue()}，返回 MP 写入数据库的值 */
    @Override
    public Integer getValue() {
        return this.code;
    }

    /**
     * 解析数据库值（封闭域语义：范围外取值视为脏数据，抛异常 fail-fast）
     *
     * @param code 数据库存储值（0 / 1），可为 null
     * @return 对应枚举；code 为 null 时返回 null
     * @throws IllegalArgumentException code 非法且非 null
     */
    public static OssFileStatusEnum parse(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> CLEANED;
            case 1 -> AVAILABLE;
            default -> throw new IllegalArgumentException("未知的 OssFileStatusEnum code: " + code);
        };
    }

}
