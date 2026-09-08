package com.yeungzhy.yeed.common.core.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSON 报文凭据脱敏：按字段名打码，补齐 {@link SensitiveTextUtil} 覆盖不到的盲区
 *
 * <p>与 {@link SensitiveTextUtil} 互补，不是替代：
 * <ul>
 *   <li>{@link SensitiveTextUtil} 按值识别有格式特征的数据（手机号、身份证、邮箱），与字段名无关，
 *       但识别不了无格式特征的数据——密码、JWT、API 密钥长得千差万别；
 *   <li>本类按键名识别凭据。密码、令牌、密钥这类数据只能靠字段名指明。
 * </ul>
 *
 * <p>用 JSON token 解析而不是正则：正则写不出可靠的键值匹配——键与值之间的空格、嵌套对象、
 * 对象数组、值内转义引号，任一种常见形态都能让它失配或越界匹配。
 *
 * <p>本类不委托 {@link SensitiveTextUtil}，两者完全独立；本类只认键名，没有凭据键名的值一律原样输出。
 * 需要两类脱敏时由调用方组合，顺序必须是先按键、后按值：
 * <pre>{@code SensitiveTextUtil.mask(SensitiveJsonUtil.mask(json))}</pre>
 * 按键打码是整值替换，凭据零残留；反过来先按值，形似手机号的密码会被打码成 {@code 138****5678}，
 * 反而留下 7 位明文。两类脱敏都幂等，顺序颠倒也不破坏 JSON 结构，但只有上述顺序能保证零残留。
 *
 * @author yeungzhy
 * @since 2026-08-29
 * @see SensitiveTextUtil
 * @see SensitiveType#CREDENTIAL
 */
@Slf4j
public final class SensitiveJsonUtil {
    private SensitiveJsonUtil() {}

    /** 源自 {@link JacksonUtil#newDefaultMapper()}：全项目 Jackson 配置的唯一来源 */
    private static final com.fasterxml.jackson.core.JsonFactory JSON_FACTORY = JacksonUtil.newDefaultMapper().getFactory();

    /**
     * 可安全通配的后缀：业务字段不会以这些词结尾，用 {@code endsWith} 放开匹配即可覆盖
     * {@code password / newPassword / accessToken / clientSecret}，且不误伤业务字段
     */
    private static final List<String> CREDENTIAL_SUFFIXES =
            List.of("password", "passwd", "pwd", "secret", "token");

    /**
     * 必须精确匹配的字段名（大小写不敏感）：这些词所在的后缀族在业务字段中广泛存在，
     * 通配必然大面积误伤，于是只枚举确定是凭据的具体名字
     */
    private static final Set<String> CREDENTIAL_EXACT_KEYS = Set.of(
            "credential", "authorization",
            "smscode", "verifycode", "validationcode", "captcha", "captchacode",
            "privatekey", "secretkey", "apikey", "accesskeyid");

    /**
     * 便宜的前置判断：文本里是否可能含凭据字段，只决定要不要解析 JSON，不参与最终判定
     * <p>命中后仍需走 {@link #isCredentialKey(String)} 精确判断。正则由上面两份名单自动派生，
     * 禁止手写——派生后新增字段名即自动纳入前置判断，不存在漏改可能
     */
    private static final Pattern CREDENTIAL_KEY_HINT = Pattern.compile("(?i)(?:"
            + Stream.concat(CREDENTIAL_SUFFIXES.stream(), CREDENTIAL_EXACT_KEYS.stream())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"))
            + ")");

    /**
     * 把 JSON 报文中所有凭据字段的值替换为 {@code ***}
     *
     * <p> 解析失败时原样返回并记 warn：报文不是合法 JSON（多半已被上游截断）时结构信息不可用，
     * 而本类只认键名，无结构即无从判定，只能放弃，这意味着可能有凭据漏网，值得关注。
     * 调用方随后执行的 {@link SensitiveTextUtil#mask(String)} 不受影响：按值识别不依赖 JSON 结构
     *
     * @param json 待脱敏文本；null / 空串原样返回
     * @return 脱敏后的文本
     */
    public static String mask(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        if (!CREDENTIAL_KEY_HINT.matcher(json).find()) {
            return json;
        }

        StringWriter out = new StringWriter(json.length() + 64);
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
                return json;
            }
            try (JsonGenerator generator = JSON_FACTORY.createGenerator(out)) {
                maskValue(parser, generator);
            }
        } catch (IOException e) {
            log.warn("Cannot mask credential fields, the raw text is returned as-is", e);
            return json;
        }
        return out.toString();
    }

    // =========== 内部辅助方法 ===========

    /** 按当前 token 的类型分派：容器递归，标量直接拷贝 */
    private static void maskValue(JsonParser parser, JsonGenerator generator) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            return;
        }
        switch (token) {
            case START_OBJECT -> maskObject(parser, generator);
            case START_ARRAY -> maskArray(parser, generator);
            default -> generator.copyCurrentEvent(parser);
        }
    }

    /**
     * 遍历对象字段：命中凭据键且值为标量则写 {@code ***}，否则原样拷贝（含嵌套递归）
     * <p>值为对象 / 数组时不打码——无法把整个子树压成一个 {@code ***} 还保持 JSON 合法——
     * 而是继续递归，确保嵌套深处的 {@code user.password} 同样被拦下
     */
    private static void maskObject(JsonParser parser, JsonGenerator generator) throws IOException {
        generator.copyCurrentEvent(parser);
        while (parser.nextToken() != JsonToken.END_OBJECT && parser.currentToken() != null) {
            String fieldName = parser.currentName();
            generator.writeFieldName(fieldName);

            parser.nextToken();
            JsonToken valueToken = parser.currentToken();
            // 值为 null 时保留 null：把"没传密码"记成 *** 属于凭空捏造
            if (valueToken != JsonToken.VALUE_NULL
                    && !valueToken.isStructStart()
                    && isCredentialKey(fieldName)) {
                generator.writeString(SensitiveType.CREDENTIAL.mask(parser.getText()));
            } else {
                maskValue(parser, generator);
            }
        }
        generator.copyCurrentEvent(parser);
    }

    private static void maskArray(JsonParser parser, JsonGenerator generator) throws IOException {
        generator.copyCurrentEvent(parser);
        while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
            maskValue(parser, generator);
        }
        generator.copyCurrentEvent(parser);
    }

    /** 字段名是否指向凭据（大小写不敏感：{@code password} / {@code Password} / {@code smsCode} 均命中） */
    private static boolean isCredentialKey(String fieldName) {
        String key = fieldName.toLowerCase(Locale.ROOT);
        return CREDENTIAL_EXACT_KEYS.contains(key)
                || CREDENTIAL_SUFFIXES.stream().anyMatch(key::endsWith);
    }

}
