package com.yeungzhy.yeed.oss.sys.file.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OSS 文件可用状态枚举
 *
 * <p>值域封闭于 {0,1}：{@link #AVAILABLE} 是正常态，{@link #CLEANED} 是对象过期或删除后的终态，
 * 生命周期已闭环；新增中间态须同步评估 {@link #parse(Integer)} 的 fail-fast 语义
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
