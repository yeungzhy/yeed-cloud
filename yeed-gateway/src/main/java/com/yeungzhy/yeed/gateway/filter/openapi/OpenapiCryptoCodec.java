package com.yeungzhy.yeed.gateway.filter.openapi;

import com.yeungzhy.yeed.common.core.crypto.AesUtil;
import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import com.yeungzhy.yeed.common.core.crypto.RsaUtil;
import com.yeungzhy.yeed.gateway.config.ApiSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * OpenApi 报文加解密器（RSA 解会话 key + AES-256-GCM 解包体与响应加密）
 *
 * <p>平台私钥（{@link CryptoProperties}）在构造期完成解析自检：配置类故障启动期即暴露，
 * 运行期解密失败可安心视为客户端密文坏（统一 400，不区分原因）
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
@Component
public class OpenapiCryptoCodec {

    private static final String KEY_ALGORITHM = "RSA";

    /** 会话密钥长度（协议固定 AES-256） */
    private static final int SESSION_KEY_BYTES = 32;

    /** AES-GCM 密文布局 IV(12)||密文||TAG(16)，故密文比明文固定多 28 字节 */
    private static final int CIPHER_OVERHEAD = 12 + 16;

    private static final byte[] EMPTY_BYTES = new byte[0];

    /** 平台 RSA 私钥（PKCS#8、Base64）：构造期已做解析自检，运行期只可能是客户端密文坏 */
    private final String platformPrivateKey;

    public OpenapiCryptoCodec(CryptoProperties cryptoProperties, ApiSecurityProperties properties) {
        this.platformPrivateKey = cryptoProperties.getRsa().getPrivateKey();
        if (properties.isEnabled()) {
            validatePlatformPrivateKey();
        }
    }

    /**
     * 解出会话 AES key 并解包体密文
     *
     * @param header 已解析的加密头（提供 X-Enc-Key 与请求 AAD）
     * @param cipher 密文请求体（空时跳过包体解密）
     * @return 明文请求体与会话 key
     */
    public Decrypted decrypt(EncryptedRequestHeader header, byte[] cipher) {
        byte[] sessionKey = decryptSessionKey(header);
        if (cipher.length == 0) {
            return new Decrypted(EMPTY_BYTES, sessionKey);
        }
        if (cipher.length < CIPHER_OVERHEAD) {
            throw cryptoBad();
        }
        byte[] plainText;
        try {
            plainText = AesUtil.decrypt(sessionKey, cipher, header.requestAad());
        } catch (RuntimeException e) {
            log.warn("OpenApi body 解密失败 appId={}", header.appId());
            throw cryptoBad();
        }
        return new Decrypted(plainText, sessionKey);
    }

    /**
     * 响应加密：整体加密下游明文，AAD 回显请求的 timestamp|nonce
     *
     * @param sessionKey 会话 AES key（32B）
     * @param plainText 下游明文响应体
     * @param aad 响应 AAD 字节
     * @return 密文字节
     */
    public byte[] encrypt(byte[] sessionKey, byte[] plainText, byte[] aad) {
        return AesUtil.encrypt(sessionKey, plainText, aad);
    }

    /**
     * RSA 私钥解 X-Enc-Key，得会话 AES key（Base64(32B) 文本）
     *
     * @param header 已解析的加密头
     * @return 会话 AES key
     */
    private byte[] decryptSessionKey(EncryptedRequestHeader header) {
        String base64Key;
        try {
            base64Key = RsaUtil.decrypt(platformPrivateKey, header.encKey());
        } catch (RuntimeException e) {
            log.warn("OpenApi X-Enc-Key 解密失败 appId={}", header.appId());
            throw cryptoBad();
        }
        byte[] sessionKey;
        try {
            sessionKey = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw cryptoBad();
        }
        if (sessionKey.length != SESSION_KEY_BYTES) {
            throw cryptoBad();
        }
        return sessionKey;
    }

    /**
     * 构造统一密文错误（不区分具体原因，避免被探测）
     *
     * @return 协议错误信号
     */
    private static ApiSecurityRejectException cryptoBad() {
        return new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_CRYPTO_BAD, "请求格式非法");
    }

    /**
     * 构造期校验平台私钥已配置且可解析（PKCS#8）
     */
    private void validatePlatformPrivateKey() {
        if (!StringUtils.hasText(platformPrivateKey)) {
            throw new IllegalStateException("api-security 已开启，但未配置 crypto.rsa.private-key（平台 RSA 私钥）");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(platformPrivateKey);
            KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("api-security 平台 RSA 私钥非法：必须是 PKCS#8 格式的 Base64 字符串", e);
        }
    }

    /**
     * 解密产物
     *
     * @param plainText 明文请求体
     * @param sessionKey 会话 AES key
     */
    public record Decrypted(byte[] plainText, byte[] sessionKey) { }

}
