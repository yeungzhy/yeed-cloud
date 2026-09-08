package com.yeungzhy.yeed.common.web.clean;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 入参字符串清洗注解：标注在 DTO 字符串字段上，Jackson 反序列化时按 {@link CleanLevel} 依次清洗后注入
 *
 * <p> 使用约束：
 * <ul>
 *   <li>只影响 HTTP JSON 反序列化入口（Controller {@code @RequestBody}）；程序内直接构造 DTO
 *       或 Feign 传递不经清洗，需要保证时在调用侧自行处理
 *   <li>清洗先于 Service 执行，业务校验天然基于清洗后的值
 *   <li>{@code value} 是清洗级别序列，按声明顺序依次执行；单级别可省略数组花括号，
 *       如 {@code @CleanString(CleanLevel.ALL)}
 * </ul>
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
     * 清洗级别序列，默认仅去首尾空白
     *
     * @return 按声明顺序执行的清洗级别；不配置时为 {@link CleanLevel#TRIM}
     */
    CleanLevel[] value() default {CleanLevel.TRIM};

}
