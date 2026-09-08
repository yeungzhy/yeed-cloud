package com.yeungzhy.yeed.common.core.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.yeungzhy.yeed.common.core.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import static com.yeungzhy.yeed.common.core.constant.Constant.DATE_TIME_PATTERN;

/**
 * 基于 {@link ObjectMapper} 的 JSON 操作静态工具类，全项目 JSON 读写的唯一出口
 *
 * <p> 异常策略：底层异常统一包装为 {@link RuntimeException}（保留 cause），信息只携带目标类型 / key / path
 * 等元信息，不含 JSON 报文内容（避免手机号、邮箱等敏感数据落盘），业务代码无需到处 try-catch
 * <ul>
 *   <li>抛出路径不打印日志：序列化 / 解析 / 转换失败一律 throw，由全局异常处理器统一记录，
 *       避免同一异常在工具类和处理器中各打一次造成日志翻倍
 *   <li>吞掉路径才打印日志：字段读取 {@code getXxx} 对「字段缺失 / 类型不匹配」返回 null 并记 warn，
 *       此处无异常可抛，不记则彻底失声
 * </ul>
 *
 * <p> 取值方法绝不静默返回默认值：{@code asInt()} 对非法文本返回 0、{@code asText()} 对对象节点返回空串、
 * 超范围整数还会静默截断，默认值会把「读错了」伪装成「读到了」
 *
 * <p> null 入参：读方法（parseXxx / getXxx / convertValue / treeToValue）返回安全默认值，不抛异常
 * {@link #toJsonStr(Object)} 传入 null 返回字符串 {@code "null"}（业界共识，同 Hutool / Fastjson2）
 *
 * <p> null 字段：默认输出 null（Jackson 原生的 {@code ALWAYS}，保证 round-trip 无损，与 Hutool / Fastjson2
 * 的默认相反），展示 / 传输场景要精简报文时用 {@link #toJsonStrIgnoreNull(Object)}，
 * 不要改全局 inclusion，本类实例同时服务 HTTP 出参、Feign 编解码与 Redis 序列化
 *
 * <p> 复用：同一 JSON 需多次取值时，先 {@link #parseTree(String)} 一次，再复用节点版 {@code getXxx(JsonNode, String)} 重载
 *
 * @author yeungzhy
 * @since 2026-08-01
 * @see JacksonMapperRegistrar
 */
@Slf4j
public final class JacksonUtil {
    private JacksonUtil() {}

    // 裁剪占位符：按「被省略的是什么」区分，便于排查时一眼判断触发了哪条阈值
    /**
     * 纯文本省略符：字符串字段截长、非 JSON 降级路径共用
     *
     * <p> 刻意不带类型标记：截短本身已自解释（值明显变短且以 {@code ...} 收尾）；且长报文里被截短的字符串
     * 可能有上百处，逐处追加标记纯属噪音，还会污染降级路径的输出
     */
    private static final String PRUNE_ELLIPSIS = "...";

    /** 深度超限：整棵子树被省略，无数量可统计，子树内容未解析正是省内存的前提 */
    private static final String PRUNE_OMITTED_DEPTH = "...(depth)";

    /** 数组元素超限：{@code %d} 为被省略的元素个数，运行时拼装（数量对判断「原报文多大」最有价值） */
    private static final String PRUNE_OMITTED_ARRAY = "...(+%d more)";

    /**
     * 生效的 {@link ObjectMapper}
     * 未启动器时为 {@link #newDefaultMapper()} 兜底实例，两者配置一致
     */
    private static volatile ObjectMapper mapper = newDefaultMapper();

    /**
     * 忽略 null 字段的 mapper，仅供 {@link #toJsonStrIgnoreNull(Object)} 使用
     *
     * <p> 由生效 mapper {@code copy()} 派生：忽略 null 只是局部诉求，不能污染 {@link #mapper}
     * （HTTP 出参、Feign 编解码、Redis 序列化共用同一个实例）
     *
     * <p> 与 {@link #mapper} 成对维护，随 {@link #bind(ObjectMapper)} 一并重建；copy 是快照，
     * bind 之后对生效 mapper 的改动不会同步过来
     */
    private static volatile ObjectMapper nonNullMapper = newNonNullMapper(mapper);

    /**
     * 绑定生效的 {@link ObjectMapper}
     *
     * <p> 仅供容器启动时调用：业务代码替换全局实例会导致各层序列化行为不一致
     */
    static void bind(ObjectMapper objectMapper) {
        mapper = objectMapper;
        nonNullMapper = newNonNullMapper(objectMapper);
    }

    /**
     * 复制出一个忽略 null 字段的 mapper
     *
     * <p> copy 出的实例有独立的序列化器缓存，故只在初始化 / {@link #bind(ObjectMapper)} 时调用，不在序列化热路径上
     */
    private static ObjectMapper newNonNullMapper(ObjectMapper source) {
        return source.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 构建标准配置的 {@link ObjectMapper}，保证有/无 Spring 上下文时序列化行为一致
     *
     * <p> 全项目 {@link ObjectMapper} 配置的唯一来源：时间格式化、Long 转 String、忽略未知字段等全局约定
     */
    public static ObjectMapper newDefaultMapper() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(Constant.DATE_TIME_PATTERN);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(Constant.DATE_PATTERN);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(Constant.TIME_PATTERN);

        ObjectMapper objectMapper = new ObjectMapper();

        // java.util.Date 走 dateFormat，非线程安全由 ObjectMapper 内部克隆保证
        objectMapper.setDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN));
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // Java 8+ 时间类型：序列化与反序列化用同一批格式化器，避免进出格式不一致
        objectMapper.registerModule(new JavaTimeModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtf))
                .addSerializer(LocalDate.class, new LocalDateSerializer(df))
                .addSerializer(LocalTime.class, new LocalTimeSerializer(tf))
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtf))
                .addDeserializer(LocalDate.class, new LocalDateDeserializer(df))
                .addDeserializer(LocalTime.class, new LocalTimeDeserializer(tf))
        );

        // Long 转 String，解决雪花 ID 前端精度丢失
        objectMapper.registerModule(new SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(Long.TYPE, ToStringSerializer.instance)
        );

        // 未知字段忽略：前端多传字段不报错，避免前后端字段不同步即 500
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 时间不输出时间戳、不携带时区 ID，格式统一由上方格式化器控制
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false);

        return objectMapper;
    }


    // ============ 序列化：对象 -> JSON ========================
    /**
     * 对象转 JSON 字符串
     *
     * <p> 传入 null 返回字符串 {@code "null"}，而非 Java null
     */
    public static String toJsonStr(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(serializationFailed(obj), e);
        }
    }

    /**
     * 对象转 JSON 字符串，并忽略值为 null 的字段
     *
     * <p> 仅适用于展示 / 传输场景（日志打印、调试、报文瘦身）。审计落库与契约出参请用 {@link #toJsonStr(Object)}：
     * null 被裁掉后「字段值为 null」与「字段不存在」不再可区分，而这种区分正是审计报文的价值所在
     *
     * <p> 优先级：类 / 字段上的 {@link JsonInclude} 注解高于本方法所用 mapper 的全局设置，
     * 被显式标注为 {@code Include.ALWAYS} 的类不会因本方法被裁剪
     *
     * <p> {@code Map} / {@link JsonNode} 中值为 null 的条目同样会被裁掉
     */
    public static String toJsonStrIgnoreNull(Object obj) {
        try {
            return nonNullMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(serializationFailed(obj), e);
        }
    }

    /**
     * 对象转带缩进换行的 JSON 字符串，适合日志 / 调试展示
     *
     * <p> 首行无前导空行，结果仍是合法 JSON 字面量
     */
    public static String toPrettyJsonStr(Object obj) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(serializationFailed(obj), e);
        }
    }

    /**
     * 对象转 JSON 字节数组，常用于 RPC / Redis 等传输场景
     */
    public static byte[] toJsonBytes(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new RuntimeException(serializationFailed(obj), e);
        }
    }


    // ============ 反序列化：JSON -> 对象 ======================
    /** JSON 字符串转 Java 对象 */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(clazz.getName()), e);
        }
    }

    /**
     * JSON 字符串转泛型对象，用于 {@code List<User>} / {@code Map<String, User>} 等带实参的场景
     *
     * <p> 用法：{@code parseObject(json, new TypeReference<List<User>>() {})}
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(typeReference.getType().getTypeName()), e);
        }
    }

    /** JSON 字节数组转 Java 对象，常用于读取 HTTP 响应体 */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null) {
            return null;
        }
        try {
            return mapper.readValue(bytes, clazz);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(clazz.getName()), e);
        }
    }

    /** JSON 字节数组转泛型对象，用法同 {@link #parseObject(String, TypeReference)} */
    public static <T> T parseObject(byte[] bytes, TypeReference<T> typeReference) {
        if (bytes == null) {
            return null;
        }
        try {
            return mapper.readValue(bytes, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(typeReference.getType().getTypeName()), e);
        }
    }

    /** JSON 字符串转 List，元素类型由 {@code clazz} 指定 */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed("List<" + clazz.getName() + ">"), e);
        }
    }

    /** JSON 字符串转原始 List，元素为 Map / 基础类型 */
    public static List<Object> parseArray(String json) {
        return parseObject(json, new TypeReference<>() {});
    }

    /** JSON 字符串转 {@code Map<String, Object>} */
    public static Map<String, Object> parseMap(String json) {
        return parseObject(json, new TypeReference<>() {});
    }

    /** JSON 字符串转指定键值类型的 Map */
    public static <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (Exception e) {
            String target = "Map<%s, %s>".formatted(keyClass.getName(), valueClass.getName());
            throw new RuntimeException(deserializationFailed(target), e);
        }
    }

    /**
     * JSON 字符串转 {@link JsonNode} 树模型，适合结构动态、字段不确定的场景
     *
     * <p> 会物化整棵树，MB 级报文请改用 {@link #pruneJson(String)} 或 {@link #parseRootScalars(byte[], Set)}
     */
    public static JsonNode parseTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(JsonNode.class.getName()), e);
        }
    }


    // ============ 类型转换：对象 -> 对象 ========================
    /**
     * 对象类型转换（Map -> Bean、Bean -> Map、POJO -> POJO）
     * 
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        try {
            return mapper.convertValue(fromValue, toValueType);
        } catch (Exception e) {
            String message = conversionFailed(fromValue.getClass().getName(), toValueType.getName());
            throw new RuntimeException(message, e);
        }
    }

    /** 对象类型转换（支持泛型），用法同 {@link #parseObject(String, TypeReference)} */
    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        if (fromValue == null) {
            return null;
        }
        try {
            return mapper.convertValue(fromValue, toValueTypeRef);
        } catch (Exception e) {
            String message = conversionFailed(fromValue.getClass().getName(), toValueTypeRef.getType().getTypeName());
            throw new RuntimeException(message, e);
        }
    }

    /** Bean 转 {@code Map<String, Object>} */
    public static Map<String, Object> beanToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return mapper.convertValue(obj, new TypeReference<>() {});
        } catch (Exception e) {
            String message = conversionFailed(obj.getClass().getName(), "Map<String, Object>");
            throw new RuntimeException(message, e);
        }
    }

    /** Map 转 Bean */
    public static <T> T mapToBean(Map<String, ?> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        try {
            return mapper.convertValue(map, clazz);
        } catch (Exception e) {
            String message = conversionFailed("Map<String, ?>", clazz.getName());
            throw new RuntimeException(message, e);
        }
    }

    /** {@link JsonNode} 转 Java 对象 */
    public static <T> T treeToValue(JsonNode node, Class<T> clazz) {
        if (isNull(node)) {
            return null;
        }
        try {
            return mapper.treeToValue(node, clazz);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(clazz.getName()), e);
        }
    }

    /** {@link JsonNode} 转泛型对象，用法同 {@link #parseObject(String, TypeReference)} */
    public static <T> T treeToValue(JsonNode node, TypeReference<T> typeReference) {
        if (isNull(node)) {
            return null;
        }
        try {
            return mapper.convertValue(node, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(deserializationFailed(typeReference.getType().getTypeName()), e);
        }
    }

    /** Java 对象转 {@link JsonNode}，便于动态构建 JSON */
    public static JsonNode valueToTree(Object value) {
        try {
            return mapper.valueToTree(value);
        } catch (Exception e) {
            throw new RuntimeException(serializationFailed(value), e);
        }
    }


    // ============ JsonNode 构造 ================================
    /** 创建空 {@link ObjectNode}，可链式 set 数据 */
    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /** 创建空 {@link ArrayNode} */
    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }


    // ============ JSON 字段读取（get 家族）=====================
    /*
     * 每个取值方法提供 JsonNode 版（核心实现）+ String 版（委托）两个重载
     * String 版等价于 parseTree(json) 后再取节点版，每次调用都会重新解析，
     * 同一 JSON 需多次取值时先 parseTree 一次并复用节点版
     */
    /** 读取字段节点，节点为空或字段不存在返回 null */
    public static JsonNode get(JsonNode node, String key) {
        if (isNull(node)) {
            return null;
        }
        return node.get(key);
    }

    /** 读取字段节点（String 版），返回 {@link JsonNode} 便于后续操作 */
    public static JsonNode get(String json, String key) {
        return get(parseTree(json), key);
    }

    /**
     * 读取字符串字段
     *
     * <p> 字段缺失 / 非标量（对象、数组）返回 null：{@code asText()} 对对象节点返回空串，会把结构错误吞成正常值
     */
    public static String getString(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        log.warn("Cannot read String from non-scalar node, key={}, value={}", key, value);
        return null;
    }

    /** 读取字符串字段（String 版），等价于 {@link #getString(JsonNode, String)} */
    public static String getString(String json, String key) {
        return getString(parseTree(json), key);
    }

    /** 读取字符串字段，字段缺失或类型不匹配时返回 {@code defaultValue} */
    public static String getString(String json, String key, String defaultValue) {
        String value = getString(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取整数字段
     *
     * <p> 字段缺失 / 非数字 / 超出 int 范围（如 3000000000）返回 null：
     * {@code asInt()} 会静默截断成另一个合法值，读错与读到无法区分
     */
    public static Integer getInteger(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.valueOf(value.asText());
            } catch (NumberFormatException ignored) {
                // 文本非数字，落入下方统一告警
            }
        }
        log.warn("Cannot read Integer from node, key={}, value={}", key, value);
        return null;
    }

    /** 读取整数字段（String 版），等价于 {@link #getInteger(JsonNode, String)} */
    public static Integer getInteger(String json, String key) {
        return getInteger(parseTree(json), key);
    }

    /** 读取整数字段，字段缺失或类型不匹配时返回 {@code defaultValue} */
    public static Integer getInteger(String json, String key, Integer defaultValue) {
        Integer value = getInteger(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取长整数字段
     *
     * <p> 字段缺失 / 非数字 / 超出 long 范围返回 null，理由同 {@link #getInteger(JsonNode, String)}
     */
    public static Long getLong(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.valueOf(value.asText());
            } catch (NumberFormatException ignored) {
                // 文本非数字，落入下方统一告警
            }
        }
        log.warn("Cannot read Long from node, key={}, value={}", key, value);
        return null;
    }

    /** 读取长整数字段（String 版），等价于 {@link #getLong(JsonNode, String)} */
    public static Long getLong(String json, String key) {
        return getLong(parseTree(json), key);
    }

    /** 读取长整数字段，字段缺失或类型不匹配时返回 {@code defaultValue} */
    public static Long getLong(String json, String key, Long defaultValue) {
        Long value = getLong(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取布尔字段
     *
     * <p> 字段缺失 / 非布尔 / 非 true、false 文本返回 null：{@code asBoolean()} 对非法文本返回 false，
     * 会把脏数据当成「显式关闭」，语义恰好相反
     */
    public static Boolean getBoolean(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            String text = value.asText();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            log.warn("Cannot read Boolean from non-boolean text, key={}, value={}", key, text);
            return null;
        }
        log.warn("Cannot read Boolean from node, key={}, value={}", key, value);
        return null;
    }

    /** 读取布尔字段（String 版），等价于 {@link #getBoolean(JsonNode, String)} */
    public static Boolean getBoolean(String json, String key) {
        return getBoolean(parseTree(json), key);
    }

    /** 读取布尔字段，字段缺失或类型不匹配时返回 {@code defaultValue} */
    public static Boolean getBoolean(String json, String key, Boolean defaultValue) {
        Boolean value = getBoolean(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取双精度字段
     *
     * <p> 字段缺失 / 非数字 / 文本非数字返回 null，理由同 {@link #getInteger(JsonNode, String)}
     */
    public static Double getDouble(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.valueOf(value.asText());
            } catch (NumberFormatException ignored) {
                // 文本非数字，落入下方统一告警
            }
        }
        log.warn("Cannot read Double from node, key={}, value={}", key, value);
        return null;
    }

    /** 读取双精度字段（String 版），等价于 {@link #getDouble(JsonNode, String)} */
    public static Double getDouble(String json, String key) {
        return getDouble(parseTree(json), key);
    }

    /**
     * 读取高精度数字字段
     * 避免 ouble 中转丢精度
     */
    public static BigDecimal getBigDecimal(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ignored) {
                // 文本非数字，落入下方统一告警
            }
        }
        log.warn("Cannot read BigDecimal from node, key={}, value={}", key, value);
        return null;
    }

    /** 读取高精度数字字段（String 版），等价于 {@link #getBigDecimal(JsonNode, String)} */
    public static BigDecimal getBigDecimal(String json, String key) {
        return getBigDecimal(parseTree(json), key);
    }

    /** 读取高精度数字字段，字段缺失或类型不匹配时返回 {@code defaultValue} */
    public static BigDecimal getBigDecimal(String json, String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取嵌套对象并转为指定类型
     *
     * <p> 与标量取值相反，类型转换失败直接抛异常：嵌套结构错了属于数据契约问题，静默返回 null 会难以定位
     */
    public static <T> T getObject(JsonNode node, String key, Class<T> clazz) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return mapper.convertValue(value, clazz);
        } catch (Exception e) {
            throw new RuntimeException(readFailed("key=" + key, clazz.getName()), e);
        }
    }

    /** 读取嵌套对象并转为指定类型（String 版），等价于 {@link #getObject(JsonNode, String, Class)} */
    public static <T> T getObject(String json, String key, Class<T> clazz) {
        return getObject(parseTree(json), key, clazz);
    }

    /** 读取嵌套对象并转为泛型类型，用于 {@code Map<String, List<User>>} 等嵌套结构 */
    public static <T> T getObject(JsonNode node, String key, TypeReference<T> typeReference) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return mapper.convertValue(value, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(readFailed("key=" + key, typeReference.getType().getTypeName()), e);
        }
    }

    /** 读取嵌套对象并转为泛型类型（String 版），等价于 {@link #getObject(JsonNode, String, TypeReference)} */
    public static <T> T getObject(String json, String key, TypeReference<T> typeReference) {
        return getObject(parseTree(json), key, typeReference);
    }

    /**
     * 读取嵌套数组并转为 List
     *
     * <p> 嵌套泛型（如 {@code List<List<User>>}）请用 {@link #getObject(JsonNode, String, TypeReference)}
     */
    public static <T> List<T> getArray(JsonNode node, String key, Class<T> clazz) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return mapper.convertValue(value,
                    mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException(readFailed("key=" + key, "List<" + clazz.getName() + ">"), e);
        }
    }

    /** 读取嵌套数组并转为 List（String 版），等价于 {@link #getArray(JsonNode, String, Class)} */
    public static <T> List<T> getArray(String json, String key, Class<T> clazz) {
        return getArray(parseTree(json), key, clazz);
    }


    // ============ 路径读取（支持 a.b.c 点号表达式）=============
    /**
     * 按路径读取标量文本，路径形如 {@code user.address.city}
     *
     * <p> 仅支持点号对象链，不支持数组下标（{@code a[0].b} 请用 {@link #parseTree(String)}
     * 配合 {@link #get(JsonNode, String)} 逐层取）；路径不存在或命中的是非标量则返回 null
     */
    public static String getByPath(String json, String path) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        return node.isValueNode() ? node.asText() : null;
    }

    /** 按路径读取并转为指定类型，路径不存在返回 null，转换失败抛异常 */
    public static <T> T getByPath(String json, String path, Class<T> clazz) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        try {
            return mapper.convertValue(node, clazz);
        } catch (Exception e) {
            throw new RuntimeException(readFailed("path=" + path, clazz.getName()), e);
        }
    }

    /** 按路径读取并转为泛型类型，用法同 {@link #parseObject(String, TypeReference)} */
    public static <T> T getByPath(String json, String path, TypeReference<T> typeReference) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        try {
            return mapper.convertValue(node, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(readFailed("path=" + path, typeReference.getType().getTypeName()), e);
        }
    }

    /**
     * 按点号路径逐层下钻取节点
     *
     * <p> 每次调用都重新 {@link #parseTree(String)}，同一报文多次取值请勿走路径版
     */
    private static JsonNode getByPathNode(String json, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        JsonNode node = parseTree(json);
        for (String key : path.split("\\.")) {
            if (isNull(node)) {
                return null;
            }
            node = node.get(key);
        }
        return node;
    }


    // ============ JSON 校验 ====================================
    /**
     * 判断是否为合法 JSON 文档，对象 / 数组 / 标量均可（{@code "123"}、{@code [1,2]}、{@code "abc"} 都算）
     *
     * <p> 空白与 null 一律判否。整份报文会被解析一遍，仅做校验时请勿在热路径调用
     */
    public static boolean isJson(String json) {
        return parseToNode(json) != null;
    }

    /** 判断是否为 JSON 对象（{@code {...}}） */
    public static boolean isJsonObject(String json) {
        JsonNode node = parseToNode(json);
        return node != null && node.isObject();
    }

    /** 判断是否为 JSON 数组（{@code [...]}） */
    public static boolean isJsonArray(String json) {
        JsonNode node = parseToNode(json);
        return node != null && node.isArray();
    }


    // ============ 大报文裁剪 ====================================
    /**
     * 流式裁剪 JSON 报文，输出仍是合法 JSON，用于操作日志 / 审计等只留摘要的场景
     *
     * <p> 按 {@link JsonPruneOptions#DEFAULT} 裁剪
     *
     * <p> 与「先建树再改树」的本质区别：全程 token 级处理，{@link JsonParser#skipChildren()} 跳过的子树
     * 不解析、不物化成 {@link JsonNode} / Map，峰值内存只与输出长度成正比、与输入长度无关，
     * 这是唯一能处理 MB 级报文的方案（建树方案的内存通常是报文的数倍）
     *
     * <p> 降级：入参不是合法 JSON（或已被上游按字节截断成半截报文）时无法按结构裁剪，记 warn 后降级为
     * {@link JsonPruneOptions#maxTotalLength()} 长度的纯文本截断，不抛异常
     *
     * <p> 输出长度是硬上限，可直接反推 DB 列长：两条路径统一受 {@link JsonPruneOptions#maxTotalLength()}
     * 约束，公式见 {@link JsonPruneOptions}。预算耗尽后停止输出，借 Jackson 默认开启的
     * {@code JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT} 自动补齐未闭合的括号
     *
     * <p> 输出不可反序列化回原类型：被省略的内容按成因记为不同占位符（{@code "...(depth)"} /
     * {@code "...(+N more)"}），与数字、布尔等元素类型混杂；字符串字段也已被
     * {@link #abbreviate(String, int)} 截短，语义上不再等于原值。这是刻意取舍，别去修，
     * 换成同类型哨兵（数字写 0、对象写 {}）虽能让反序列化成功，却产出无法与真实数据区分的假数据，
     * 排查时比解析失败更具误导性。JSON 本就没有「省略标记」语义，任何占位符都必然与真实值歧义
     */
    public static String pruneJson(String json) {
        return pruneJson(json, JsonPruneOptions.DEFAULT);
    }

    /**
     * 流式裁剪 JSON 报文（自定义阈值），契约同 {@link #pruneJson(String)}
     *
     * @param json    待裁剪的 JSON 字符串，null / 空白原样返回
     * @param options 裁剪阈值，null 抛 {@link NullPointerException}
     */
    public static String pruneJson(String json, JsonPruneOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        if (json == null || json.isBlank()) {
            return json;
        }
        StringWriter out = new StringWriter(Math.min(json.length(), options.maxTotalLength()));
        PruneBudget budget = new PruneBudget(out.getBuffer(), options.maxTotalLength());
        try (JsonParser parser = mapper.getFactory().createParser(json);
             JsonGenerator generator = mapper.getFactory().createGenerator(out)) {
            while (parser.nextToken() != null && !budget.exhausted()) {
                copyPruned(parser, generator, 1, options, budget);
            }
            // 预算耗尽时会留下未闭合的容器：close() 借 AUTO_CLOSE_JSON_CONTENT（默认开启）自动补齐
        } catch (IOException e) {
            // 吞掉异常返回降级结果，必须记录：不记则彻底失声
            log.warn("Cannot prune JSON by structure, fallback to plain text truncation", e);
            return abbreviate(json, options.maxTotalLength());
        }
        return out.toString();
    }

    /**
     * 按阈值拷贝当前 token 到生成器：对象递归、数组限量、字符串截长，其余原样透传
     *
     * <p> 每个写入单元（字段名、字符串值）写入前先问 {@link PruneBudget}，耗尽则整棵子树
     * {@link JsonParser#skipChildren()} 丢弃并逐层返回，一个字符都不再写
     */
    private static void copyPruned(JsonParser parser, JsonGenerator generator, int depth,
                                   JsonPruneOptions options, PruneBudget budget) throws IOException {
        switch (parser.currentToken()) {
            case START_OBJECT -> {
                if (depth > options.maxDepth()) {
                    parser.skipChildren();
                    generator.writeString(PRUNE_OMITTED_DEPTH);
                    return;
                }
                generator.writeStartObject();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (budget.exhausted()) {
                        parser.skipChildren();
                        return;
                    }
                    // 字段名同样截长：JSON key 长度无上限，不截则单次写入无上界，输出总长就永远保证不了
                    generator.writeFieldName(abbreviate(parser.currentName(), options.maxFieldLength()));
                    parser.nextToken();
                    copyPruned(parser, generator, depth + 1, options, budget);
                }
                generator.writeEndObject();
            }
            case START_ARRAY -> {
                if (depth > options.maxDepth()) {
                    parser.skipChildren();
                    generator.writeString(PRUNE_OMITTED_DEPTH);
                    return;
                }
                generator.writeStartArray();
                int total = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (budget.exhausted()) {
                        parser.skipChildren();
                        return;
                    }
                    if (++total <= options.maxArrayElements()) {
                        copyPruned(parser, generator, depth + 1, options, budget);
                    } else {
                        // 关键：被丢弃的元素整棵子树跳过，不解析、不物化
                        parser.skipChildren();
                    }
                }
                if (total > options.maxArrayElements()) {
                    generator.writeString(PRUNE_OMITTED_ARRAY.formatted(total - options.maxArrayElements()));
                }
                generator.writeEndArray();
            }
            case VALUE_STRING -> generator.writeString(abbreviate(parser.getText(), options.maxFieldLength()));
            default -> generator.copyCurrentEvent(parser);
        }
    }

    /**
     * 裁剪输出预算：已写字符数达到上限后立刻停止输出，是输出长度可保证的唯一手段
     *
     * <p> 直接读 {@link StringBuffer#length()} 而非自行累加，避免漏算生成器写入的转义字符与结构符号
     *
     * <p> 计数口径偏保守：此处按 UTF-16 代码单元计，MySQL utf8mb4 的 {@code varchar(n)} 按字符计，
     * 增补平面字符（emoji）在 Java 侧占 2 个单元却只算 1 个字符，故 Java 侧达标必然 MySQL 侧达标
     */
    private record PruneBudget(StringBuffer sink, int maxLength) {
        boolean exhausted() {
            return sink.length() >= maxLength;
        }
    }

    /**
     * 大报文裁剪阈值，四个值各自解决一类超大，均为正整数（非法值在构造时 fail-fast 抛
     * {@link IllegalArgumentException}，避免带着错误配置静默产出无意义的日志）
     * <ul>
     *   <li>{@code maxFieldLength}，单个字符串过长（Base64 文件内容）。同时约束字段名：
     *       JSON key 长度同样无上限，不约束则单次写入无上界，输出总长就永远保证不了
     *   <li>{@code maxArrayElements}，数组元素过多（万行列表导出）
     *   <li>{@code maxDepth}，嵌套过深（深层树形菜单）。根对象为第 1 层，
     *       超过该层数的子树整棵省略为 {@code "...(depth)"}
     *   <li>{@code maxTotalLength}，输出硬上限（字符数），合法 / 非法 JSON 两条路径统一受它约束
     * </ul>
     *
     * <p> DB 列长公式（唯一需要记的式子）：前三个阈值只削减单个维度的膨胀，字段数 × 元素数 × 嵌套深度
     * 相乘仍可无限膨胀，只有 {@code maxTotalLength} 是真正的总闸门。溢出量 = 最后一次写入的粒度：
     * <pre>
     * 输出字符数 ≤ maxTotalLength + max(2 * maxFieldLength + 2, 21) + 4
     * ⇒ maxTotalLength ≤ 列长 N - max(2 * maxFieldLength + 2, 21) - 4
     * </pre>
     * 例：MySQL {@code varchar(2000)} 配 {@code maxFieldLength=300} → {@code maxTotalLength ≤ 1396}
     *
     * <p> 取值自洽：{@code maxTotalLength} 太小会让前三个阈值形同虚设（1024 配 300 时一个字段就占 602 字符，
     * 保留 10 个数组元素根本放不下）。经验值：{@code maxTotalLength ≥ maxArrayElements * maxFieldLength}
     */
    public record JsonPruneOptions(int maxFieldLength, int maxArrayElements, int maxDepth, int maxTotalLength) {

        /** 默认阈值：单字段 200 字符、数组保留 10 个元素、深度 10 层、输出硬上限 2 KB */
        public static final JsonPruneOptions DEFAULT = new JsonPruneOptions(200, 10, 10, 2048);

        public JsonPruneOptions {
            if (maxFieldLength < 1 || maxArrayElements < 1 || maxDepth < 1 || maxTotalLength < 1) {
                throw new IllegalArgumentException(
                        "JsonPruneOptions requires positive values, got: maxFieldLength=%d, maxArrayElements=%d, maxDepth=%d, maxTotalLength=%d"
                                .formatted(maxFieldLength, maxArrayElements, maxDepth, maxTotalLength));
            }
        }
    }


    // ============ 大报文字段抽取 ================================
    /**
     * 只挑出根层的若干标量字段，用于报文很大、但只需要其中两三个字段的场景
     *
     * <p> 与 {@link #parseTree(String)} 的本质区别：本方法流式扫描、不建树，目标字段之外的子树一律
     * {@link JsonParser#skipChildren()} 跳过（不解析、不物化），峰值内存只与目标字段的文本长度有关、
     * 与报文长度无关；{@code parseTree} 会物化整棵树，内存通常是报文的数倍
     *
     * <p> 返回节点的类型与原文一致：数字仍是数字节点、布尔仍是布尔节点，可直接 put 到别的节点上，
     * 不做一律转字符串的降维
     *
     * <p> 降级：报文本身非法时返回已读到的部分而非抛异常，调用方多为尽力而为的旁路逻辑（如操作日志），
     * 不该因旁路失败影响主流程，故此处记 warn
     *
     * @param json       待读取的 JSON 字节；非 JSON 对象（数组、纯文本）返回空节点
     * @param fieldNames 关注的字段名，null / 空返回空节点
     * @return 只含命中字段的 {@link ObjectNode}；缺失的字段不出现在结果里，
     *         调用方需用 {@code has(field)} 区分「字段缺失」与「字段值为 null」
     */
    public static ObjectNode parseRootScalars(byte[] json, Set<String> fieldNames) {
        ObjectNode result = createObjectNode();
        if (json == null || json.length == 0 || fieldNames == null || fieldNames.isEmpty()) {
            return result;
        }
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return result;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                /*
                 * 只取标量：对象 / 数组一律跳过，null 也跳过，它不代表「读到了值」
                 * 如此调用方才能靠 has(field) 区分「字段缺失」与「显式 null」
                 */
                if (fieldNames.contains(name) && value != null && value.isScalarValue() && value != JsonToken.VALUE_NULL) {
                    result.set(name, parser.readValueAsTree());
                } else {
                    parser.skipChildren();
                }
                if (result.size() == fieldNames.size()) {
                    // 目标已全部命中，剩下的输入不必再扫
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read root scalars from JSON, fields={}", fieldNames, e);
        }
        return result;
    }


    // =========== 内部辅助方法 \ 避免漂移方法 ===========

    // --- 异常信息 ---
    /**
     * 构造序列化失败信息
     *
     * <p> 异常信息统一由私有方法生成，保证全类文案、字段顺序、分隔符一致，且只携带类型等元信息
     */
    private static String serializationFailed(Object source) {
        String sourceType = source == null ? "null" : source.getClass().getName();
        return "JSON serialization failed, sourceType=" + sourceType;
    }

    /** 构造反序列化失败信息，{@code targetType} 为期望得到的目标类型（含泛型实参） */
    private static String deserializationFailed(String targetType) {
        return "JSON deserialization failed, targetType=" + targetType;
    }

    /** 构造对象转换失败信息（{@code convertValue} / {@code beanToMap} / {@code mapToBean}） */
    private static String conversionFailed(String sourceType, String targetType) {
        return "JSON convert failed, sourceType=" + sourceType + ", targetType=" + targetType;
    }

    /**
     * 构造字段读取失败信息（{@code getObject} / {@code getArray} / {@code getByPath}）
     *
     * <p> {@code location} 为定位信息，如 {@code key=xxx}、{@code path=a.b.c}
     */
    private static String readFailed(String location, String targetType) {
        return "JSON convert failed, " + location + ", targetType=" + targetType;
    }

    // --- 文本截断 ---
    /** 按最大长度缩写文本，超出部分以 {@link #PRUNE_ELLIPSIS} 收尾，总长度不超过 {@code maxLength} */
    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + PRUNE_ELLIPSIS;
    }

    // --- 节点解析与判定 ---
    /** 解析为节点，非法 JSON 返回 null（{@code isJson} 家族据此判否，不记日志） */
    private static JsonNode parseToNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** JSON 的 null 与 Java 的 null 一并判空：{@code NullNode} 不是 null 引用，直接判 {@code == null} 会漏 */
    private static boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }

}
