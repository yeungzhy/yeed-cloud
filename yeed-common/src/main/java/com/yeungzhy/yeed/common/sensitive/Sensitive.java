package com.yeungzhy.yeed.common.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解
 *
 * <p> 标注在 VO 字段上，Jackson 序列化时由 {@link SensitiveJsonSerializer} 按指定
 * {@link SensitiveType} 对明文执行掩码，输出到 HTTP 响应 JSON。
 *
 * <p> <b>职责边界（重要）</b>：本注解<b>只做明文→掩码</b>，不做密文→明文解密。
 * 调用方需保证被标注字段在序列化时已是明文（解密由 {@code FieldCryptoInterceptor}
 * 在 MyBatis 出库阶段完成）。把解密塞进 serializer 会引入 AES key 注入与职责混淆，明确禁止。
 *
 * <p> <b>View 差异</b>：列表与详情需要不同掩码策略时，通过不同 VO 区分
 * （如 {@code SysUserPageVO.email} 标注、{@code SysUserDetailVO.email} 不标注），
 * 不在同 VO 上提供运行时开关。
 *
 * <p> <b>非 Spring Bean</b>：注解与 serializer 均非 Spring Bean，由 Jackson 注解扫描驱动，
 * 无需在 yeed-common 的 {@code AutoConfiguration.imports} 中注册。
 *
 * @author yeungzhy
 * @since 2026-08-07
 */
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏类型，决定掩码策略
     *
     * @return {@link SensitiveType} 枚举值
     */
    SensitiveType type();
}
