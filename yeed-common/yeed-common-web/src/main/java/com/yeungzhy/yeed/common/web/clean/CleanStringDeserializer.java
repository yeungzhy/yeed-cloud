package com.yeungzhy.yeed.common.web.clean;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

/**
 * {@link CleanString} 的 Jackson 反序列化器。
 *
 * <p>通过 {@link ContextualDeserializer} 在构造阶段把清洗级别序列注入实例并由
 * Jackson 缓存复用，反序列化执行时零反射；levels 构造后不可变，实例线程安全。
 *
 * @author yeungzhy
 * @since 2026-08-14
 * @see CleanString
 * @see CleanLevel
 */
public class CleanStringDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    /**
     * 绑定的清洗级别序列，由 {@link #createContextual} 注入，构造后不可变。
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
        // 未标注 @CleanString 不应进入此反序列化器，真出现说明注解配置错误，返回默认实例兜底避免 NPE
        if (property == null) {
            return this;
        }
        CleanString annotation = property.getAnnotation(CleanString.class);
        if (annotation == null) {
            return this;
        }
        // 返回带 levels 的实例，Jackson 会缓存到该字段的 deserializer 上
        return new CleanStringDeserializer(annotation.value());
    }
}
