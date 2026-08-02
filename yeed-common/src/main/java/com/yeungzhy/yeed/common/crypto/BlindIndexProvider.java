package com.yeungzhy.yeed.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 盲索引工具类 (基于 HMAC-SHA256)
 * <p> 用于在加密数据库中生成可查询、不可逆的索引值，相同输入 + 相同密钥 + 相同上下文盐 = 相同输出
 */
public final class BlindIndexProvider {

    private static final String HMAC_ALGO = "HmacSHA256";

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
        return HexFormat.of().formatHex(hmac);
    }

    /**
     * 核心 HMAC-SHA256 计算逻辑： HMAC( masterKey, contextSalt + plaintext )
     * <p>
     * 采用 "先拼接上下文盐，再计算 HMAC" 的方式，使得同一密钥下不同字段的盲索引互相独立。
     * </p>
     */
    private byte[] computeHmac(String plaintext) {
        if (plaintext == null) {
            plaintext = "";
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