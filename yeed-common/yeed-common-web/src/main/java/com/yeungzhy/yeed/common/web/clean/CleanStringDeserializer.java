package com.yeungzhy.yeed.common.web.clean;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

/**
 * {@link CleanString} 的 Jackson 反序列化器
 *
 * <p> 通过 {@link ContextualDeserializer} 在构造阶段把清洗级别序列注入实例并由
 * Jackson 缓存复用，反序列化执行时零反射；levels 构造后不可变，实例线程安全
 *
 * @author yeungzhy
 * @since 2026-08-14
 * @see CleanString
 * @see CleanLevel
 */
public class CleanStringDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    /**
     * 绑定的清洗级别序列，由 {@link #createContextual} 注入，构造后不可变
     */
    private CleanLevel[] levels;

    // Jackson 反射实例化需要无参构造器；levels 待 createContextual 注入
    public CleanStringDeserializer() {}

    private CleanStringDeserializer(CleanLevel[] levels) {
        // clone 防御：注解内部数组可能被框架缓存复用，避免外部意外修改污染全局
        this.levels = levels.clone();
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        // null 透传（JSON null 不参与清洗）
        if (value == null) {
            return null;
        }
        // 按声明顺序依次清洗（如 TRIM+EMOJI：先去首尾空白再去 emoji）
        for (CleanLevel level : levels) {
            value = level.clean(value);
        }
        return value;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        if (property == null) {
            // 无字段上下文（如类型级使用）时保持共享默认实例
            return this;
        }
        CleanString annotation = property.getAnnotation(CleanString.class);
        if (annotation == null) {
            // 字段未标注 @CleanString 却走到了这里，多半是注解配置错误，不擅自清洗，返回默认实例
            return this;
        }
        // 返回携带该字段 levels 的新实例：实例会被 Jackson 缓存为该字段专用反序列化器，构造一次即可
        return new CleanStringDeserializer(annotation.value());
    }
}
