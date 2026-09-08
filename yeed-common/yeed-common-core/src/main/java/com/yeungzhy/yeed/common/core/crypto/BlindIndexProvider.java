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

    private final byte[] masterKey;
    private final byte[] contextSalt;


    public BlindIndexProvider(String keyStr, String contextSaltStr) {
        if (keyStr == null || keyStr.isEmpty()) {
            throw new IllegalArgumentException("Blind index master key must not be null or empty, check config");
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
     */
    public String generateHex(String plaintext) {
        byte[] hmac = computeHmac(plaintext);
        // 截断到 OUTPUT_LENGTH 字节，避免完整 32 字节作为索引过长影响存储/查询性能
        byte[] truncated = new byte[OUTPUT_LENGTH];
        System.arraycopy(hmac, 0, truncated, 0, OUTPUT_LENGTH);
        return HexFormat.of().formatHex(truncated);
    }

    /**
     * 核心 HMAC-SHA256 计算逻辑：HMAC( masterKey, contextSalt + plaintext )
     * <p>采用"先拼接上下文盐，再计算 HMAC"的方式，使同一密钥下不同字段的盲索引互相独立。
     */
    private byte[] computeHmac(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null, decide null handling at caller side");
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