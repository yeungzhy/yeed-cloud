package com.yeungzhy.yeed.common.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 盲索引工具类（基于 HMAC-SHA256）
 *
 * <p> 在加密数据库中生成可查询、不可逆的索引值：相同输入 + 相同密钥 + 相同上下文盐 = 相同输出，
 * 因此可对密文列建索引后按明文等值查询
 *
 * <p> 实例由 {@link com.yeungzhy.yeed.common.core.config.CryptoAutoConfiguration} 基于
 * {@link CryptoProperties} 创建；业务侧的盲索引 Bean（idCard / phone / email 等数据库场景专用）
 * 由 common-data 的 BlindIndexAutoConfiguration 注册
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
public final class BlindIndexProvider {

    private static final String HMAC_ALGO = "HmacSHA256";
    /** 输出长度（截断到 12 字节 / 96 bit，碰撞概率在 2^48 条记录才显著，足够绝大多数业务场景） */
    private static final int OUTPUT_LENGTH = 12;
    /** 主密钥最小字节长度（OWASP 推荐 HMAC-SHA256 密钥下限） */
    private static final int MIN_KEY_LENGTH = 32;

    private final byte[] masterKey;
    private final byte[] contextSalt;


    /**
     * 构造盲索引计算器
     *
     * <p>密钥长度下限 {@value #MIN_KEY_LENGTH} 字节：短密钥不会报错、照常产出索引，
     * 但索引可被枚举反推明文，属静默失效，必须在构造期拦下而非留到运行期无从察觉
     *
     * @param keyStr         HMAC 主密钥，UTF-8 字节长度至少 {@value #MIN_KEY_LENGTH}
     * @param contextSaltStr 上下文盐，隔离同一密钥下不同字段的索引空间
     * @throws IllegalArgumentException 密钥或盐为 null、空，或密钥长度不足
     */
    public BlindIndexProvider(String keyStr, String contextSaltStr) {
        if (keyStr == null || keyStr.isEmpty()) {
            throw new IllegalArgumentException("Blind index master key must not be null or empty, check config");
        }
        if (keyStr.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException("Blind index master key must be at least "
                    + MIN_KEY_LENGTH + " bytes (UTF-8), check config");
        }
        if (contextSaltStr == null || contextSaltStr.isEmpty()) {
            throw new IllegalArgumentException("Blind index contextSaltStr must not be null or empty, check config");
        }
        this.masterKey = keyStr.getBytes(StandardCharsets.UTF_8).clone();
        this.contextSalt = contextSaltStr.getBytes(StandardCharsets.UTF_8).clone();
    }

    /**
     * 生成盲索引 (Hex 十六进制输出)
     *
     * @param plaintext 原始数据
     * @return 小写十六进制字符串
     * @throws IllegalArgumentException plaintext 为 null 或空
     */
    public String generateHex(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("plaintext must not be null or empty, decide empty handling at caller side");
        }
        byte[] hmac = computeHmac(plaintext);
        // 截断到 OUTPUT_LENGTH 字节，避免完整 32 字节作为索引过长影响存储/查询性能
        byte[] truncated = new byte[OUTPUT_LENGTH];
        System.arraycopy(hmac, 0, truncated, 0, OUTPUT_LENGTH);
        return HexFormat.of().formatHex(truncated);
    }

    /**
     * 核心 HMAC-SHA256 计算逻辑：HMAC( masterKey, contextSalt + plaintext )
     *
     * <p>采用"先拼接上下文盐，再计算 HMAC"的方式，使同一密钥下不同字段的盲索引互相独立
     *
     * <p>空串与 null 同等拒绝：空串算出的索引是固定值，所有未填该字段的记录会撞同一条唯一索引，
     * 而报错发生在写库时，那时排查者看到的是"为什么两个不同用户手机号冲突"，很难想到源头是空串
     */
    private byte[] computeHmac(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("plaintext must not be null or empty");
        }
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        // 1. 拼接盐和原始数据: salt || data
        byte[] combined = new byte[contextSalt.length + plainBytes.length];
        System.arraycopy(contextSalt, 0, combined, 0, contextSalt.length);
        System.arraycopy(plainBytes, 0, combined, contextSalt.length, plainBytes.length);

        // 2. 计算 HMAC-SHA256
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, HMAC_ALGO);
            mac.init(keySpec);
            return mac.doFinal(combined);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 algorithm not available or key invalid", e);
        }
    }


}