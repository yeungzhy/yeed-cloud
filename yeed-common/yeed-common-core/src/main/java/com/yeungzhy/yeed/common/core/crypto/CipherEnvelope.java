package com.yeungzhy.yeed.common.core.crypto;

/**
 * 密文信封格式工具 - 只负责对密文做包装/识别/解包，不负责加解密。
 *
 * <p>存储格式约定：<pre>ENC(Base64密文)</pre>
 *
 * <p>职责边界：本类只描述"密文长什么样、如何包/如何拆"
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
public final class CipherEnvelope {

    private CipherEnvelope() {}

    /** 信封前缀 */
    private static final String PREFIX = "ENC(";
    /** 信封后缀 */
    private static final String SUFFIX = ")";

    /**
     * 给密文套上信封
     *
     * @param cipherText Base64 密文（{@link AesUtil} 的加密结果）
     * @return {@code ENC(cipherText)}
     * @throws IllegalArgumentException cipherText 为 null 或空
     */
    public static String wrap(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            throw new IllegalArgumentException("cipherText must not be null or empty");
        }
        return PREFIX + cipherText + SUFFIX;
    }

    /**
     * 判断一个字符串是否已带密文信封（前缀 {@code ENC(} 且后缀 {@code )}）
     *
     * @param value 待判断值
     * @return true=已带信封（已加密），false=未带信封（明文或历史裸密文）
     */
    public static boolean isWrapped(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    /**
     * 拆掉信封，返回内部密文
     *
     * @param value 已带信封的值
     * @return 信封内的 Base64 密文
     * @throws IllegalArgumentException value 未带信封
     */
    public static String unwrap(String value) {
        if (!isWrapped(value)) {
            throw new IllegalArgumentException("value is not wrapped with " + PREFIX + "..." + SUFFIX);
        }
        return value.substring(PREFIX.length(), value.length() - SUFFIX.length());
    }


}
