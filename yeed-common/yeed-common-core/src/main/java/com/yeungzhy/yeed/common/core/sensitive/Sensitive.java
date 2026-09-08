package com.yeungzhy.yeed.common.core.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解：标注在 VO 字段上，Jackson 序列化时按 {@link SensitiveType} 对明文掩码后输出
 *
 * <p> 示例：{@code @Sensitive(type = SensitiveType.EMAIL) private String email;}
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see SensitiveType
 * @see SensitiveJsonSerializer
 */
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏类型（必填，无默认值），决定掩码策略
     * <p> 可选值及掩码形态见 {@link SensitiveType} 各常量
     *
     * @return 脱敏类型
     */
    SensitiveType type();
}
