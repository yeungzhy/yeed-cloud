package com.yeungzhy.yeed.common.core.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 基于 {@link ObjectMapper} 的 JSON 操作薄封装（Spring Bean）
 *
 * <p>通过 {@link com.yeungzhy.yeed.common.core.config.JacksonAutoConfiguration} 注册为 Spring Bean，
 * 内部持有的 {@link ObjectMapper} 即全局唯一实例，序列化行为与 HTTP 层 / Redis 层完全一致；
 * 时间格式、Long→String、忽略未知字段等全局约定由 {@code JacksonAutoConfiguration} 收敛，本类不再重复配置。
 *
 * <p><b>命名基线</b>：序列化用 {@code toXxx}（toJsonStr / toPrettyJsonStr / toJsonBytes），
 * 反序列化用 {@code parseXxx}（parseObject / parseArray / parseMap / parseTree，对齐 Fastjson2）；
 * 字段读取统一 {@code getXxx} 全称（getString / getInteger / getBoolean，不引入缩写）；
 * 类型转换沿用 Jackson 原生 {@code convertValue / treeToValue / valueToTree}。
 *
 * <p><b>异常策略</b>：底层异常统一捕获并包装为 {@link RuntimeException}（保留 cause），业务代码无需
 * 到处 try-catch；字段读取 {@code getXxx} 对"字段缺失 / 类型不匹配"返回 {@code null} 并记录 warn 日志，
 * <b>绝不静默返回默认值</b>（避免 {@code asInt()} 对非法文本返回 0、{@code asText()} 对对象节点返回空串、
 * 超范围整数截断等陷阱）。
 *
 * <p><b>null 入参策略</b>：读方法（parseXxx / getXxx / convertValue / treeToValue）入参为 null 时返回
 * 安全默认值（null / 空集合），不抛异常；{@link #toJsonStr(Object)} 传入 null 返回字符串 {@code "null"}
 * （业界共识，同 Hutool / Fastjson2）。
 *
 * <p><b>性能提示</b>：同一 JSON 需多次取值时，先 {@link #parseTree(String)} 一次得到 {@link JsonNode}，
 * 再复用节点版 {@code getXxx(JsonNode, String)} 重载，避免每次从字符串重复解析。
 *
 * @author yeungzhy
 */
@Slf4j
public class JacksonHelper {

    private final ObjectMapper objectMapper;

    public JacksonHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    // ============ 序列化：对象 -> JSON ========================
    /**
     * 对象转 JSON 字符串
     * <p>传入 null 返回字符串 {@code "null"}（与 Hutool / Fastjson2 行为一致）
     */
    public String toJsonStr(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Jackson 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * 美化输出（带缩进换行），适合日志 / 调试展示
     * <p>返回的字符串首行无前导空行，是合法 JSON 字面量
     */
    public String toPrettyJsonStr(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Jackson 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * 对象转 JSON 字节数组，常用于 RPC / Redis 等传输场景
     */
    public byte[] toJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("Jackson 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }


    // ============ 反序列化：JSON -> 对象 ======================
    /**
     * JSON 字符串转 Java 对象
     * 等价于 Fastjson2 JSON.parseObject(str, Class) / Hutool JSONUtil.toBean(str, Class)
     */
    public <T> T parseObject(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转泛型对象，用于 List&lt;User&gt;、Map&lt;String, User&gt; 等场景
     * <p>用法：JacksonHelper.parseObject(json, new TypeReference&lt;List&lt;User&gt;&gt;() {})
     */
    public <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字节数组转 Java 对象，常用于读取 HTTP 响应体
     */
    public <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字节数组转泛型对象，用法同 {@link #parseObject(String, TypeReference)}
     */
    public <T> T parseObject(byte[] bytes, TypeReference<T> typeReference) {
        if (bytes == null) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, typeReference);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转 List
     */
    public <T> List<T> parseArray(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转原始 List（元素为 Map / 基础类型）
     */
    public List<Object> parseArray(String json) {
        return parseObject(json, new TypeReference<List<Object>>() {});
    }

    /**
     * JSON 字符串转 Map&lt;String, Object&gt;
     */
    public Map<String, Object> parseMap(String json) {
        return parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * JSON 字符串转指定键值类型的 Map
     */
    public <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转 JsonNode 树模型，适合结构动态、字段不确定的场景
     * 等价于 Fastjson2 JSON.parse(str)
     */
    public JsonNode parseTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }


    // ============ 类型转换：对象 -> 对象 ========================
    /**
     * 对象类型转换（如 Map -> Bean、Bean -> Map、POJO -> POJO）
     * 等价于 Hutool BeanUtil.toBean / Fastjson2 JSON.parseObject(JSON.toJSONString(obj), clazz)
     */
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(fromValue, toValueType);
        } catch (Exception e) {
            log.error("Jackson 类型转换失败, toType={}", toValueType, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * 对象类型转换（支持泛型）
     */
    public <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        if (fromValue == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(fromValue, toValueTypeRef);
        } catch (Exception e) {
            log.error("Jackson 类型转换失败, toType={}", toValueTypeRef, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * Bean 转 Map&lt;String, Object&gt;
     */
    public Map<String, Object> beanToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Jackson Bean 转 Map 失败", e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * Map 转 Bean
     */
    public <T> T mapToBean(Map<String, ?> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(map, clazz);
        } catch (Exception e) {
            log.error("Jackson Map 转 Bean 失败, toType={}", clazz, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * JsonNode 转 Java 对象
     */
    public <T> T treeToValue(JsonNode node, Class<T> clazz) {
        if (isNull(node)) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, clazz);
        } catch (Exception e) {
            log.error("Jackson treeToValue 失败, toType={}", clazz, e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JsonNode 转泛型对象，用法同 {@link #treeToValue(JsonNode, Class)}
     */
    public <T> T treeToValue(JsonNode node, TypeReference<T> typeReference) {
        if (isNull(node)) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, typeReference);
        } catch (Exception e) {
            log.error("Jackson treeToValue 失败, toType={}", typeReference, e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * Java 对象转 JsonNode，便于动态构建 JSON
     */
    public JsonNode valueToTree(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            log.error("Jackson valueToTree 失败", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }


    // ============ JsonNode 构造 ================================
    /**
     * 创建空 ObjectNode，可链式 set 数据
     */
    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    /**
     * 创建空 ArrayNode
     */
    public ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }


    // ============ JSON 字段读取（get 家族）=====================
    // 每个取值方法提供「JsonNode 版（核心实现）+ String 版（委托）」：同一 JSON 多次取值时
    // 先 parseTree 一次再复用节点版，避免重复解析；String 版等价于 parseTree(json) 后取节点版。
    /**
     * 从节点中读取字段，节点为空或未找到返回 null
     */
    public JsonNode get(JsonNode node, String key) {
        if (isNull(node)) {
            return null;
        }
        return node.get(key);
    }

    /**
     * 从 JSON 字符串中读取字段，返回 JsonNode 便于后续操作
     */
    public JsonNode get(String json, String key) {
        return get(parseTree(json), key);
    }

    /**
     * 读取字符串字段
     * <p>字段缺失 / 非标量（对象、数组）返回 null，避免 {@code asText()} 对对象节点返回空串的陷阱
     */
    public String getString(JsonNode node, String key) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        log.warn("Jackson getString 字段非标量值, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取字符串字段（String 版，等价于 {@link #getString(JsonNode, String)}）
     */
    public String getString(String json, String key) {
        return getString(parseTree(json), key);
    }

    /**
     * 读取字符串字段，字段缺失或类型不匹配时返回默认值
     */
    public String getString(String json, String key, String defaultValue) {
        String value = getString(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取整数字段
     * <p>字段缺失 / 非整数 / 超出 int 范围（如 3000000000）返回 null，
     * 避免 {@code asInt()} 静默截断为错误值的陷阱
     */
    public Integer getInteger(JsonNode node, String key) {
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
        log.warn("Jackson getInteger 字段无法安全转换为 int, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取整数字段（String 版，等价于 {@link #getInteger(JsonNode, String)}）
     */
    public Integer getInteger(String json, String key) {
        return getInteger(parseTree(json), key);
    }

    /**
     * 读取整数字段，字段缺失或类型不匹配时返回默认值
     */
    public Integer getInteger(String json, String key, Integer defaultValue) {
        Integer value = getInteger(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取长整数字段
     * <p>字段缺失 / 非整数 / 超出 long 范围返回 null，避免 {@code asLong()} 静默截断的陷阱
     */
    public Long getLong(JsonNode node, String key) {
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
        log.warn("Jackson getLong 字段无法安全转换为 long, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取长整数字段（String 版，等价于 {@link #getLong(JsonNode, String)}）
     */
    public Long getLong(String json, String key) {
        return getLong(parseTree(json), key);
    }

    /**
     * 读取长整数字段，字段缺失或类型不匹配时返回默认值
     */
    public Long getLong(String json, String key, Long defaultValue) {
        Long value = getLong(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取布尔字段
     * <p>字段缺失 / 非布尔 / 非 "true"/"false" 文本返回 null，
     * 避免 {@code asBoolean()} 对非法文本返回 false 的陷阱
     */
    public Boolean getBoolean(JsonNode node, String key) {
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
            log.warn("Jackson getBoolean 字段文本非布尔值, key={}, value={}", key, text);
            return null;
        }
        log.warn("Jackson getBoolean 字段非布尔类型, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取布尔字段（String 版，等价于 {@link #getBoolean(JsonNode, String)}）
     */
    public Boolean getBoolean(String json, String key) {
        return getBoolean(parseTree(json), key);
    }

    /**
     * 读取布尔字段，字段缺失或类型不匹配时返回默认值
     */
    public Boolean getBoolean(String json, String key, Boolean defaultValue) {
        Boolean value = getBoolean(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取双精度字段
     * <p>字段缺失 / 非数字 / 文本非数字返回 null
     */
    public Double getDouble(JsonNode node, String key) {
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
            } catch (NumberFormatException e) {
                log.warn("Jackson getDouble 字段文本非数字, key={}, value={}", key, value.asText());
                return null;
            }
        }
        log.warn("Jackson getDouble 字段非数字类型, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取双精度字段（String 版，等价于 {@link #getDouble(JsonNode, String)}）
     */
    public Double getDouble(String json, String key) {
        return getDouble(parseTree(json), key);
    }

    /**
     * 读取高精度数字字段
     * <p>字段缺失 / 非数字 / 文本非数字返回 null
     */
    public BigDecimal getBigDecimal(JsonNode node, String key) {
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
            } catch (NumberFormatException e) {
                log.warn("Jackson getBigDecimal 字段文本非数字, key={}, value={}", key, value.asText());
                return null;
            }
        }
        log.warn("Jackson getBigDecimal 字段非数字类型, key={}, value={}", key, value);
        return null;
    }

    /**
     * 读取高精度数字字段（String 版，等价于 {@link #getBigDecimal(JsonNode, String)}）
     */
    public BigDecimal getBigDecimal(String json, String key) {
        return getBigDecimal(parseTree(json), key);
    }

    /**
     * 读取高精度数字字段，字段缺失或类型不匹配时返回默认值
     */
    public BigDecimal getBigDecimal(String json, String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(json, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 读取嵌套对象并转为指定类型
     * 等价于 Fastjson2 jsonObject.getObject(key, Class)
     */
    public <T> T getObject(JsonNode node, String key, Class<T> clazz) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Jackson getObject 转换失败, key={}, toType={}", key, clazz, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * 读取嵌套对象并转为指定类型（String 版，等价于 {@link #getObject(JsonNode, String, Class)}）
     */
    public <T> T getObject(String json, String key, Class<T> clazz) {
        return getObject(parseTree(json), key, clazz);
    }

    /**
     * 读取嵌套对象并转为泛型类型，用于 Map&lt;String, List&lt;User&gt;&gt; 等嵌套结构
     */
    public <T> T getObject(JsonNode node, String key, TypeReference<T> typeReference) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, typeReference);
        } catch (Exception e) {
            log.error("Jackson getObject 转换失败, key={}, toType={}", key, typeReference, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * 读取嵌套对象并转为泛型类型（String 版，等价于 {@link #getObject(JsonNode, String, TypeReference)}）
     */
    public <T> T getObject(String json, String key, TypeReference<T> typeReference) {
        return getObject(parseTree(json), key, typeReference);
    }

    /**
     * 读取嵌套数组并转为 List
     * <p>嵌套泛型（如 List&lt;List&lt;User&gt;&gt;）请用 {@link #getObject(JsonNode, String, TypeReference)}
     * 等价于 Fastjson2 jsonObject.getJSONArray(key).toJavaList(Class)
     */
    public <T> List<T> getArray(JsonNode node, String key, Class<T> clazz) {
        JsonNode value = get(node, key);
        if (isNull(value)) {
            return null;
        }
        try {
            return objectMapper.convertValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("Jackson getArray 转换失败, key={}, toType={}", key, clazz, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * 读取嵌套数组并转为 List（String 版，等价于 {@link #getArray(JsonNode, String, Class)}）
     */
    public <T> List<T> getArray(String json, String key, Class<T> clazz) {
        return getArray(parseTree(json), key, clazz);
    }


    // ============ 路径读取（支持 a.b.c 点号表达式）=============
    /**
     * 按路径读取字符串，支持 "user.address.city" 形式
     * <p>仅支持点号对象链，<b>不支持数组下标</b>（a[0].b，如需数组访问请用
     * {@link #parseTree(String)} + {@link #get(JsonNode, String)} 组合）；路径不存在返回 null
     * 等价于 Hutool JSONUtil.getByPath / Fastjson2 JSONPath.eval
     */
    public String getByPath(String json, String path) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        return node.isValueNode() ? node.asText() : null;
    }

    /**
     * 按路径读取并转为指定类型
     */
    public <T> T getByPath(String json, String path, Class<T> clazz) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, clazz);
        } catch (Exception e) {
            log.error("Jackson getByPath 转换失败, path={}, toType={}", path, clazz, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    /**
     * 按路径读取并转为泛型类型，用法同 {@link #getByPath(String, String, Class)}
     */
    public <T> T getByPath(String json, String path, TypeReference<T> typeReference) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, typeReference);
        } catch (Exception e) {
            log.error("Jackson getByPath 转换失败, path={}, toType={}", path, typeReference, e);
            throw new RuntimeException("JSON convert failed", e);
        }
    }

    private JsonNode getByPathNode(String json, String path) {
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
     * 判断字符串是否为合法 JSON 文档（对象、数组或标量均可，如 "123"、"[1,2]"、"{...}"、"\"abc\""）
     */
    public boolean isJson(String json) {
        return parseToNode(json) != null;
    }

    /**
     * 判断字符串是否为 JSON 对象
     */
    public boolean isJsonObject(String json) {
        JsonNode node = parseToNode(json);
        return node != null && node.isObject();
    }

    /**
     * 判断字符串是否为 JSON 数组
     */
    public boolean isJsonArray(String json) {
        JsonNode node = parseToNode(json);
        return node != null && node.isArray();
    }


    // ============ 内部辅助方法 ==================================
    private JsonNode parseToNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }

}
