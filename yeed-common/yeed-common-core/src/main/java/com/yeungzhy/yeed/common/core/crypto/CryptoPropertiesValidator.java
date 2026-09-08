package com.yeungzhy.yeed.common.core.crypto;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 加密配置启动校验器
 *
 * <p> 在 {@link CryptoProperties} 注入完成后立即校验密钥配置，任一项失败则抛异常让应用启动失败（fail-fast）
 *
 * <p> 校验内容：
 * <ul>
 *   <li>AES：Base64 解码后必须 32 字节（AES-256）
 *   <li>RSA：公钥/私钥可被 KeyFactory 解析，且公私钥配对（通过 {@link RsaUtil} 加解密验证，顺带验证工具类的 OAEP 参数）
 *   <li>HMAC：secret 的 UTF-8 字节长度至少 32（OWASP 推荐 HMAC-SHA256 密钥下限）
 * </ul>
 *
 * <p> 触发时机：{@link PostConstruct}，Bean 初始化阶段，早于 {@code ApplicationRunner}，
 * 避免配置错误时白白连接数据库/Redis/Nacos 等外部资源
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@Slf4j
public class CryptoPropertiesValidator {

    @Resource
    private CryptoProperties cryptoProperties;

    /**
     * 启动时校验加密配置，任一项失败则抛异常让应用启动失败
     *
     * @throws IllegalStateException    CryptoProperties 未注入
     * @throws IllegalArgumentException 配置缺失、格式错误或 RSA 公私钥不配对
     */
    @PostConstruct
    public void validate() {
        log.info("Starting crypto properties validation...");

        if (cryptoProperties == null) {
            throw new IllegalStateException("CryptoProperties is not configured");
        }

        validateRsa();
        validateAes();
        validateHmac();

        log.info("Crypto properties validation passed.");
    }


    /**
     * 校验 RSA 配置：公钥/私钥格式可被 KeyFactory 解析，且公私钥配对
     *
     * @throws IllegalArgumentException 公钥/私钥缺失、格式无效或配对验证失败
     */
    private void validateRsa() {
        if (cryptoProperties.getRsa() == null) {
            throw new IllegalArgumentException("RSA configuration is missing");
        }

        String publicKeyBase64 = cryptoProperties.getRsa().getPublicKey();
        String privateKeyBase64 = cryptoProperties.getRsa().getPrivateKey();

        if (publicKeyBase64 == null || publicKeyBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA public key is missing or empty");
        }
        if (privateKeyBase64 == null || privateKeyBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA private key is missing or empty");
        }

        // 解析公钥（仅验证格式以给出清晰错误信息，不保存对象；配对验证交给 RsaUtil）
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA public key format is invalid, cannot be parsed by KeyFactory", e);
        }

        // 解析私钥（仅验证格式）
        try {
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            keyFactory.generatePrivate(privateKeySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA private key format is invalid, cannot be parsed by KeyFactory", e);
        }

        // 验证公钥私钥配对：用项目内的 RsaUtil 工具类（含 OAEP 参数）验证
        String testData = "CryptoPropertiesValidator-RSA-KeyPair-Validation";
        try {
            String encrypted = RsaUtil.encrypt(publicKeyBase64, testData);
            String decrypted = RsaUtil.decrypt(privateKeyBase64, encrypted);
            if (!testData.equals(decrypted)) {
                throw new IllegalArgumentException("RSA public key and private key do not match: decryption result mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("RSA public key and private key do not match or are unusable", e);
        }

        log.info("RSA key pair validation passed (format valid and matched)");
    }


    /**
     * 校验 AES 配置：Base64 解码后必须 32 字节（AES-256）
     *
     * @throws IllegalArgumentException AES 配置缺失、Base64 非法或密钥长度不符
     */
    private void validateAes() {
        if (cryptoProperties.getAes() == null) {
            throw new IllegalArgumentException("AES configuration is missing");
        }

        String base64Key = cryptoProperties.getAes().getKey();
        if (base64Key == null || base64Key.trim().isEmpty()) {
            throw new IllegalArgumentException("AES key is missing or empty");
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AES key is not a valid Base64 string", e);
        }

        if (decodedKey.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must be 32 bytes long after Base64 decoding, but was " + decodedKey.length + " bytes");
        }

        log.info("AES key validation passed ({} bytes)", decodedKey.length);
    }


    /**
     * 校验 HMAC 配置：secret 的 UTF-8 字节长度至少 32（OWASP 推荐 HMAC-SHA256 密钥下限）
     *
     * @throws IllegalArgumentException HMAC 配置缺失或密钥长度不符
     */
    private void validateHmac() {
        if (cryptoProperties.getHmac() == null) {
            throw new IllegalArgumentException("HMAC configuration is missing");
        }

        String secret = cryptoProperties.getHmac().getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("HMAC secret is missing or empty");
        }

        int secretByteLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretByteLength < 32) {
            throw new IllegalArgumentException(
                    "HMAC secret must be at least 32 bytes long (UTF-8), but was " + secretByteLength + " bytes");
        }

        log.info("HMAC secret validation passed ({} bytes)", secretByteLength);
    }


}
