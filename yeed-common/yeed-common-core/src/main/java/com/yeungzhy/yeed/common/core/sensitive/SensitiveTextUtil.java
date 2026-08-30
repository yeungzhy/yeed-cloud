package com.yeungzhy.yeed.common.core.sensitive;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * 自由文本敏感数据脱敏：按数据本身识别并打码，不依赖字段名
 *
 * <p>全项目唯一的自由文本脱敏工具类，两条链路共用：网关访问日志直接调 {@link #mask(String)}，
 * 日志输出由 {@link SensitiveDataLogConverter}（logback 转换器）委托本类。
 *
 * <p>发现正则与掩码规则均取自 {@link SensitiveType}：新增敏感类型只需在枚举上加一个常量，
 * 此处自动生效，无需改动本类。
 *
 * <p>受正则能力约束，只能识别有格式特征的数据（手机号、身份证、邮箱）；姓名、住址这类
 * 无格式特征的信息无法自动识别，只能在 VO 出参侧用 {@link Sensitive} 按字段拦截。
 *
 * <p>与 {@link SensitiveJsonUtil} 组合时务必先按键、后按值：
 * <pre>{@code SensitiveTextUtil.mask(SensitiveJsonUtil.mask(json))}</pre>
 * 顺序颠倒时，形似手机号的密码会被打码成 {@code 138****5678} 留下 7 位明文；按此顺序则凭据
 * 先被整段打成 {@code ***}，不再匹配任何发现正则。
 *
 * @author yeungzhy
 * @since 2026-08-29
 * @see SensitiveType#discoveryPattern()
 * @see SensitiveJsonUtil
 */
public final class SensitiveTextUtil {
    private SensitiveTextUtil() {}

    /**
     * 参与文本扫描的类型：仅收集 {@link SensitiveType#discoveryPattern()} 非 null 的类型
     * <p>顺序为枚举声明顺序，稳定可预期——掩码产物不会被后续类型再次匹配（见 {@link #mask(String)}）
     */
    private static final List<SensitiveType> DISCOVERABLE_TYPES = Arrays.stream(SensitiveType.values())
            .filter(type -> Objects.nonNull(type.discoveryPattern()))
            .toList();

    /**
     * 把文本中所有可识别的敏感数据替换为对应类型的掩码
     *
     * <p>各类型依次扫描，每个类型在前一个类型的结果上继续；掩码产物不会被后续正则再次命中
     *
     * @param text 待脱敏文本；null / 空串原样返回
     * @return 脱敏后的文本
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = text;
        for (SensitiveType type : DISCOVERABLE_TYPES) {
            masked = maskMatched(masked, type);
        }
        return masked;
    }

    // =========== 内部辅助方法 ===========

    /**
     * 用 {@link Matcher} 逐位置替换：每匹配一处，就对该处原文求掩码再写入
     * <p>不用 {@code text.replace(matched, masked)} 全局子串替换——会误改文本中其他位置的相同子串
     */
    private static String maskMatched(String text, SensitiveType type) {
        Matcher matcher = type.discoveryPattern().matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start()).append(type.mask(matcher.group()));
            lastEnd = matcher.end();
        }
        return lastEnd == 0 ? text : result.append(text.substring(lastEnd)).toString();
    }

}
