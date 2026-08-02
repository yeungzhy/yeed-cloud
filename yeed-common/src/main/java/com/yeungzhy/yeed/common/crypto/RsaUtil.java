package com.yeungzhy.yeed.common.crypto;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 工具类 - JDK 标准实现，约定参数（方便多端统一）：
 * <ul>
 *     <li>Algorithm: RSA</li>
 *     <li>Mode: ECB (占位模式)</li>
 *     <li>Padding: OAEPWithSHA-256AndMGF1Padding</li>
 *     <li>Signature: SHA256withRSA</li>
 *     <li>Key Size: 2048 bit</li>
 *     <li>Key Format: X.509(SPKI)公钥 / PKCS#8私钥, Base64编码</li>
 *     <li>Charset: UTF-8</li>
 * </ul>
 */
public class RsaUtil {
    private RsaUtil() {}
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 算法 */
    private static final String KEY_ALGORITHM = "RSA";
    /** 模式 */
    private static final String MODE = "ECB";
    /** 填充 */
    private static final String PADDING = "OAEPWithSHA-256AndMGF1Padding";
    /** 完整 Transformation */
    private static final String TRANSFORMATION = KEY_ALGORITHM + "/" + MODE + "/" + PADDING;
    /** 签名算法 */
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    /** 字符集 */
    private static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;
    /** 密钥长度 */
    private static final int KEY_SIZE = 2048;

    /**
     * RSA 公钥加密
     */
    public static String encrypt(String publicKeyBase64, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("RSA 加密失败 [transformation=" + TRANSFORMATION + "]", e);
        }
    }

    /**
     * RSA 私钥解密
     */
    public static String decrypt(String privateKeyBase64, String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return "";
        }
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decryptedBytes = cipher.doFinal(cipherBytes);
            return new String(decryptedBytes, CHARSET);

        } catch (Exception e) {
            throw new RuntimeException("RSA 解密失败 [transformation=" + TRANSFORMATION + "]", e);
        }
    }

    /**
     * RSA 私钥签名（SHA256withRSA）
     *
     * @param privateKeyBase64 Base64 编码的 PKCS#8 格式私钥
     * @param plainText        待签名内容
     * @return Base64 编码的签名
     */
    public static String sign(String privateKeyBase64, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(plainText.getBytes(CHARSET));

            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);

        } catch (Exception e) {
            throw new RuntimeException("RSA 签名失败 [algorithm=" + SIGNATURE_ALGORITHM + "]", e);
        }
    }

    /**
     * RSA 公钥验签（SHA256withRSA）
     *
     * @param publicKeyBase64 Base64 编码的 X.509 格式公钥
     * @param plainText       原始内容
     * @param signBase64      Base64 编码的签名
     * @return 是否通过
     */
    public static boolean verify(String publicKeyBase64, String plainText, String signBase64) {
        if (plainText == null || plainText.isEmpty() || signBase64 == null || signBase64.isEmpty()) {
            return false;
        }
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(plainText.getBytes(CHARSET));

            byte[] signBytes = Base64.getDecoder().decode(signBase64);
            return signature.verify(signBytes);

        } catch (Exception e) {
            throw new RuntimeException("RSA 验签失败 [algorithm=" + SIGNATURE_ALGORITHM + "]", e);
        }
    }

    /**
     * 生成 RSA 密钥对（2048 位）
     *
     * @return [0]=Base64公钥(X.509), [1]=Base64私钥(PKCS#8)
     */
    public static String[] generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_SIZE, SECURE_RANDOM);
            KeyPair pair = generator.generateKeyPair();

            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

            return new String[]{publicKey, privateKey};
        } catch (Exception e) {
            throw new RuntimeException("生成密钥对失败 [keySize=" + KEY_SIZE + "]", e);
        }
    }

    // ==================== 密钥加载（内部复用）====================

    private static PublicKey loadPublicKey(String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePublic(spec);
    }

    private static PrivateKey loadPrivateKey(String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePrivate(spec);
    }

}
