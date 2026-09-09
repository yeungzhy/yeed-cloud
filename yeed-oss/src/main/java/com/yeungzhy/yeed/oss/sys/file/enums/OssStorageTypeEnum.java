package com.yeungzhy.yeed.oss.sys.file.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OSS 存储类型枚举
 *
 * <p>取值随存储提供方接入而增长（开放域），故反查方法取 {@code fromCodeOrNull} 之名，不提供封闭域 {@code parse}
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Getter
@AllArgsConstructor
public enum OssStorageTypeEnum implements IEnum<Integer> {

    /** 本地磁盘（0）：v1 唯一实现，文件存于 {@code yeed-oss.oss.local.storage-path} */
    LOCAL_DISK(0, "本地磁盘"),

    /** 阿里云 OSS（1）：预留常量，存储提供方尚未实现，当前不可作为落库值 */
    ALIYUN_OSS(1, "阿里云OSS"),

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
     * 反查数据库值（开放域查询语义）
     *
     * <p>取值随存储提供方接入而扩展，旧记录可能携带当期未定义的值，未命中返回 {@code null} 由调用方兜底，
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
