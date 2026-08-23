package com.yeungzhy.yeed.common.core.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * {@link Sensitive} 的 Jackson 序列化器。
 *
 * <p>通过 {@link ContextualSerializer} 在构造阶段把 {@link com.yeungzhy.yeed.common.core.sensitive.SensitiveType} 注入实例并由
 * Jackson 缓存复用，序列化执行时零反射；type 构造后不可变，实例线程安全。
 *
 * <p>掩码异常时降级写 null 并记录 error 日志，避免单字段脱敏失败拖垮整次响应序列化。
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see Sensitive
 * @see com.yeungzhy.yeed.common.core.sensitive.SensitiveType
 */
@Slf4j
public class SensitiveJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 绑定的脱敏类型，由 {@link #createContextual} 注入，构造后不可变。
     */
    private com.yeungzhy.yeed.common.core.sensitive.SensitiveType type;

    // Jackson 反射实例化需要无参构造器；type 待 createContextual 注入
    public SensitiveJsonSerializer() {}

    private SensitiveJsonSerializer(com.yeungzhy.yeed.common.core.sensitive.SensitiveType type) {
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        try {
            gen.writeString(type.mask(value));
        } catch (Exception e) {
            // 掩码失败降级为 null：单字段脱敏异常不应拖垮整次响应序列化
            log.error("敏感字段脱敏失败，降级输出 null [type={}]", type, e);
            gen.writeNull();
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
        // 未标注 @Sensitive 不应进入此序列化器，真出现说明注解配置错误，返回默认实例兜底避免 NPE
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            return this;
        }
        // 返回带 type 的实例，Jackson 会缓存到该字段的 serializer 上
        return new SensitiveJsonSerializer(annotation.type());
    }
}
