package com.yeungzhy.yeed.common.web.clean;

import java.util.regex.Pattern;

/**
 * 入参字符串清洗级别
 *
 * <p> 每种级别自带 {@link #clean(String)} 实现：新增清洗策略只需追加一个常量并实现 clean，
 * DTO 字段标注 {@link CleanString} 即生效；多个级别可组合（{@link CleanString#value()}
 * 为数组）并按声明顺序依次执行。清洗发生在 Jackson 反序列化入口
 * （{@link CleanStringDeserializer}），Service 层拿到的必为已清洗值
 *
 * @author yeungzhy
 * @since 2026-08-14
 * @see CleanString
 * @see CleanStringDeserializer
 */
public enum CleanLevel {

    /**
     * 去首尾空白（Unicode 感知，含全角空格）
     * <pre>{@code "  sys:user:list  " → "sys:user:list"}</pre>
     */
    TRIM {
        @Override
        public String clean(String value) {
            return value.strip();
        }
    },

    /**
     * 去除全部空白字符（空格、Tab、换行等，含串内）
     * <pre>{@code "sys: user: list" → "sys:user:list"
     * "用 户 管 理" → "用户管理"}</pre>
     */
    ALL {
        @Override
        public String clean(String value) {
            return ALL_PATTERN.matcher(value).replaceAll("");
        }
    },

    /**
     * 去除 emoji 字符（含 emoji 序列的变体选择符 VS16/零宽连接符 ZWJ，避免残留孤立字符）
     * <pre>{@code "支持👍" → "支持"
     * "👍👌" → ""}</pre>
     *
     * <p> 按 Unicode 主要 emoji 区块匹配，可能误删个别非 emoji 符号（杂项符号区），
     * 管理后台场景可接受
     */
    EMOJI {
        @Override
        public String clean(String value) {
            return EMOJI_PATTERN.matcher(value).replaceAll("");
        }
    };

    /**
     * 全部空白字符正则：编译一次静态复用
     */
    private static final Pattern ALL_PATTERN = Pattern.compile("\\s+");

    /**
     * emoji 正则：主区块 {@code 1F300-1FAFF} + 杂项符号文本形式 {@code 2600-27BF}，
     * 另补两个不在上述连续区块内的单点：变体选择符 VS16（{@code FE0F}）与零宽连接符
     * ZWJ（{@code 200D}），保证连成序列的 emoji 被整体删掉
     */
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]");

    /**
     * 按级别清洗字符串
     *
     * @param value 要清洗的字符串，不能为 null；入口对 null 已先行透传
     * @return 清洗后的字符串，恒非 null，可能为空串
     */
    public abstract String clean(String value);

}
