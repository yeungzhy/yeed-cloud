package com.yeungzhy.yeed.common.web.clean;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 入参字符串清洗注解：标注在 DTO 字符串字段上，Jackson 反序列化时按 {@link CleanLevel} 依次清洗后注入。
 *
 * <p>注意事项：
 * <ul>
 *   <li>只影响 HTTP JSON 反序列化入口（Controller {@code @RequestBody}）；
 *       程序内直接构造 DTO / Feign 传递不经清洗，如需保证需在调用侧自行处理。</li>
 *   <li>清洗先于 Service 执行，业务校验（如 notBlank、唯一性检查）天然基于清洗后值。</li>
 *   <li>{@code value} 为清洗级别序列，按声明顺序依次执行；单级别可省略花括号
 *       （如 {@code @CleanString(CleanLevel.ALL)}）。</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * &#64;CleanString
 * private String name;                        // 默认 TRIM：去首尾空白
 * &#64;CleanString(CleanLevel.ALL)
 * private String perms;                       // 去全部空白（含内部）
 * &#64;CleanString({CleanLevel.TRIM, CleanLevel.EMOJI})
 * private String remark;                      // 去首尾空白 + 去除 emoji
 * </pre>
 *
 * @author yeungzhy
 * @since 2026-08-14
 * @see CleanLevel
 * @see CleanStringDeserializer
 */
@JacksonAnnotationsInside
@JsonDeserialize(using = CleanStringDeserializer.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CleanString {

    /**
     * 清洗级别序列（默认仅去首尾空白 {@link CleanLevel#TRIM}）。
     *
     * @return 清洗级别
     */
    CleanLevel[] value() default {CleanLevel.TRIM};

}
