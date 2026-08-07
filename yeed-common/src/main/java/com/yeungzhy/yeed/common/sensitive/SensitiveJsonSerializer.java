package com.yeungzhy.yeed.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 敏感字段 Jackson 序列化器
 *
 * <p> 配合 {@link Sensitive} 注解使用：Jackson 首次构造 bean serializer 时调用一次
 * {@link #createContextual}，读取字段上的 {@code @Sensitive} 注解，生成一个
 * <b>持有具体 {@link SensitiveType}</b> 的 serializer 实例并缓存到该字段。
 *
 * <p> <b>为何用 {@link ContextualSerializer} 而非单例反射读注解</b>：
 * ContextualSerializer 是 Jackson 标准扩展点，注解中的 type 在上下文构造阶段一次性注入
 * serializer 实例，序列化执行时<b>零反射</b>。1000 条分页数据就是 1000 次序列化，
 * 若每次反射读注解开销不可接受。
 *
 * <p> <b>异常降级</b>：掩码过程若抛异常，降级写 null 并记录 error 日志，
 * 避免单字段脱敏失败把整次 HTTP 响应序列化拖崩（500）。
 *
 * @author yeungzhy
 * @since 2026-08-07
 */
@Slf4j
public class SensitiveJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 当前 serializer 实例绑定的脱敏类型，由 {@link #createContextual} 注入
     */
    private SensitiveType type;

    // Jackson 反射实例化需要无参构造器；type 留空，待 createContextual 注入
    public SensitiveJsonSerializer() {}

    private SensitiveJsonSerializer(SensitiveType type) {
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
        // 字段未标注 @Sensitive 不应进入此序列化器（@JsonSerialize 由 @Sensitive 注解驱动）；
        // 真出现说明注解配置错误，返回默认实例兜底，避免 NPE
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            return this;
        }
        // 返回带 type 的实例，Jackson 会把它缓存到该字段的 serializer 上
        return new SensitiveJsonSerializer(annotation.type());
    }
}
