package com.yeungzhy.yeed.oss.sys.file.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OSS 存储类型枚举（文件存储领域概念）
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Getter
@AllArgsConstructor
public enum OssStorageTypeEnum implements IEnum<Integer> {

    /** 本地磁盘（0）：v1 唯一实现，文件存于 {@code yeed-oss.oss.local.storage-path} */
    LOCAL_DISK(0, "本地磁盘"),

    /** 阿里云 OSS（1）：预留，接入时实现存储提供方并切换枚举 */
    ALIYUN_OSS(1, "阿里云OSS"),

    ;

    /** 数据库存储值（0-本地磁盘，1-阿里云OSS） */
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
     * 反查数据库值（开放域语义：查不到返回 null，不抛异常）
     *
     * <p>开放域枚举值会随时间扩展，旧记录可能携带当前未定义的值；反查失败返回 null、由调用方决定兜底，
     * 避免历史数据反查即崩
     *
     * @param code 数据库存储值，可为 null
     * @return 对应枚举；code 为 null 或未定义时返回 null
     */
    public static OssStorageTypeEnum fromCodeOrNull(Integer code) {
        if (code == null) {
            return null;
        }
        for (OssStorageTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

}
