package com.yeungzhy.yeed.common.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Jackson 静态工具类 (用法同 Hutool/Fastjson2)
 */
@Slf4j
@Component
public class JacksonHelper {

    @Resource
    private ObjectMapper objectMapper;


    // ============ 序列化：对象 -> JSON 字符串 ====================
    /**
     * 对象转 JSON 字符串
     * <p> 内部捕获异常，转为运行时异常，避免业务代码到处 try-catch
     */
    public String toJsonStr(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Jackson 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * 美化输出（带缩进）
     */
    public String toPrettyJsonStr(Object obj) {
        try {
            return System.lineSeparator() + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Jackson 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }


    // ============ 反序列化：JSON 字符串 -> 对象 =================
    /**
     * JSON 字符串转 Java 对象
     * 等价于 Fastjson2 JSON.parseObject(str, Class) / Hutool JSONUtil.toBean(str, Class)
     */
    public <T> T parseObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转泛型对象，用于 List＜User＞、Map＜String, User＞ 等场景
     * <p> 用法：JacksonUtil.parseObject(json, new TypeReference＜List＜User＞＞(){})
     */
    public <T> T parseObject(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字节数组转 Java 对象，常用于读取 HTTP 响应体
     */
    public <T> T parseObject(byte[] bytes, Class<T> clazz) {
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转 List
     */
    public <T> List<T> parseArray(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转原始 List（元素为 Map / 基础类型）
     */
    public List<Object> parseArray(String json) {
        return parseObject(json, new TypeReference<>() {});
    }

    /**
     * JSON 字符串转 Map＜String, Object＞
     */
    public Map<String, Object> parseMap(String json) {
        return parseObject(json, new TypeReference<>() {});
    }

    /**
     * JSON 字符串转指定键值类型的 Map
     */
    public <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (JsonProcessingException e) {
            log.error("Jackson 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * JSON 字符串转 JsonNode 树模型，适合结构动态、字段不确定的场景
     * 等价于 Fastjson2 JSON.parse(str)
     */
    public JsonNode parseTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
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
        return objectMapper.convertValue(fromValue, toValueType);
    }

    /**
     * 对象类型转换（支持泛型）
     */
    public <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        return objectMapper.convertValue(fromValue, toValueTypeRef);
    }

    /**
     * Bean 转 Map＜String, Object＞
     */
    public Map<String, Object> beanToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Map 转 Bean
     */
    public <T> T mapToBean(Map<String, ?> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        return objectMapper.convertValue(map, clazz);
    }

    /**
     * JsonNode 转 Java 对象
     */
    public <T> T treeToValue(JsonNode node, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            log.error("Jackson treeToValue 失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * Java 对象转 JsonNode，便于动态构建 JSON
     */
    public JsonNode valueToTree(Object value) {
        return objectMapper.valueToTree(value);
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


    // ============ JSON 字段读取（类似 Fastjson2/Hutool）========
    /**
     * 从 JSON 字符串中读取字段，返回 JsonNode 便于后续操作
     */
    public JsonNode get(String json, String key) {
        JsonNode node = parseTree(json);
        return node.get(key);
    }

    public String getStr(String json, String key) {
        JsonNode value = get(json, key);
        return isNull(value) ? null : value.asText();
    }

    public Integer getInt(String json, String key) {
        JsonNode value = get(json, key);
        return isNull(value) ? null : value.asInt();
    }

    public Long getLong(String json, String key) {
        JsonNode value = get(json, key);
        return isNull(value) ? null : value.asLong();
    }

    public Boolean getBool(String json, String key) {
        JsonNode value = get(json, key);
        return isNull(value) ? null : value.asBoolean();
    }

    public Double getDouble(String json, String key) {
        JsonNode value = get(json, key);
        return isNull(value) ? null : value.asDouble();
    }

    public BigDecimal getBigDecimal(String json, String key) {
        JsonNode value = get(json, key);
        if (isNull(value)) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取嵌套对象并转为指定类型
     * 等价于 Fastjson2 jsonObject.getObject(key, Class)
     */
    public <T> T getObject(String json, String key, Class<T> clazz) {
        JsonNode value = get(json, key);
        if (isNull(value)) {
            return null;
        }
        return objectMapper.convertValue(value, clazz);
    }

    /**
     * 读取嵌套数组并转为 List
     * 等价于 Fastjson2 jsonObject.getJSONArray(key).toJavaList(Class)
     */
    public <T> List<T> getArray(String json, String key, Class<T> clazz) {
        JsonNode value = get(json, key);
        if (isNull(value)) {
            return null;
        }
        return objectMapper.convertValue(value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }


    // ============ 路径读取（支持 a.b.c 点号表达式）=============
    /**
     * 按路径读取字符串，支持 "user.address.city" 形式
     * 等价于 Hutool JSONUtil.getByPath / Fastjson2 JSONPath.eval
     */
    public String getByPath(String json, String path) {
        JsonNode node = getByPathNode(json, path);
        return isNull(node) ? null : node.asText();
    }

    /**
     * 按路径读取并转为指定类型
     */
    public <T> T getByPath(String json, String path, Class<T> clazz) {
        JsonNode node = getByPathNode(json, path);
        if (isNull(node)) {
            return null;
        }
        return objectMapper.convertValue(node, clazz);
    }

    private JsonNode getByPathNode(String json, String path) {
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
     * 判断字符串是否为合法 JSON（对象或数组）
     */
    public boolean isJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && (node.isObject() || node.isArray());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为 JSON 对象
     */
    public boolean isJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为 JSON 数组
     */
    public boolean isJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    // ============ 内部辅助方法 ==================================
    private boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }

}