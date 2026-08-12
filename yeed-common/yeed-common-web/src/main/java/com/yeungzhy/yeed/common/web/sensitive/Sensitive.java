package com.yeungzhy.yeed.common.web.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解：标注在 VO 字段上，Jackson 序列化时按 {@link SensitiveType} 对明文掩码后输出。
 *
 * <p>注意事项：
 * <ul>
 *   <li>只做明文→掩码，不做密文→解密——序列化时字段必须是明文
 *       （由 {@link com.yeungzhy.yeed.common.data.mybatis.FieldCryptoInterceptor} 在 MyBatis 出库阶段保证）</li>
 *   <li>列表/详情掩码策略不同时，用不同 VO 区分标注与否，不做运行时开关</li>
 * </ul>
 *
 * <p>示例：{@code @Sensitive(type = SensitiveType.EMAIL) private String email;}
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
     * 脱敏类型（必填，无默认值），决定掩码策略。
     * 可选值及掩码形态见 {@link SensitiveType} 各常量。
     *
     * @return 脱敏类型
     */
    SensitiveType type();
}
