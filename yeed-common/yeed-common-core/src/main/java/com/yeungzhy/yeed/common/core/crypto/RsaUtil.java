package com.yeungzhy.yeed.common.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 工具类（JDK 标准实现，参数跨端约定）
 * <ul>
 *   <li>Algorithm: RSA
 *   <li>Mode: ECB（占位模式）
 *   <li>Padding: OAEPWithSHA-256AndMGF1Padding（MGF1 显式指定 SHA-256，避免 JDK 默认走 SHA-1 导致多端互通失败）
 *   <li>Signature: SHA256withRSA
 *   <li>Key Size: 2048 bit
 *   <li>Key Format: X.509(SPKI)公钥 / PKCS#8私钥，Base64 编码
 *   <li>Charset: UTF-8
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-05-22
 */
public final class RsaUtil {
    private RsaUtil() { throw new UnsupportedOperationException("Utility class cannot be instantiated"); }
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 算法 */
    private static final String KEY_ALGORITHM = "RSA";
    /** 模式 */
    private static final String MODE = "ECB";
    /** 填充 */
    private static final String PADDING = "OAEPWithSHA-256AndMGF1Padding";
    /** 完整 Transformation */
    private static final String TRANSFORMATION = KEY_ALGORITHM + "/" + MODE + "/" + PADDING;
    /**
     * OAEP 参数：主哈希 SHA-256 + MGF1 也用 SHA-256
     * <p> JDK 默认 MGF1 走 SHA-1，会导致与前端 JS/WebCrypto 等多端互通解密失败，故显式指定
     */
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    /**
     * 签名算法：SHA256withRSA（PKCS#1 v1.5）
     *
     * <p>未升级 RSASSA-PSS：v1.5 签名无已知实际攻击（Bleichenbacher 针对 v1.5 加密，与签名无关），
     * 强度对 B2B 身份认证与防抵赖足够；openapi 面向外部应用，其 SDK 普遍只支持 v1.5（RSA2）
     *
     * <p>升级收益：可证明安全性（随机预言机模型）、RFC 8017 推荐新应用使用、TLS 1.3 已强制。代价：saltLength 须多端对齐，
     * 且 JDK 验签忽略入参 saltLength（从签名恢复），契约实际只在出方向强制；签名随机化后同一明文两次签名不同
     *
     * <p>应用全部自研可控、或不再对接外部 v1.5 生态时升级，配方：
     * <pre>{@code
     * private static final String SIGNATURE_ALGORITHM = "RSASSA-PSS";
     * private static final PSSParameterSpec PSS_SPEC = new PSSParameterSpec(
     *         "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
     * // sign() / verify() 在 initSign / initVerify 之前各加 signature.setParameter(PSS_SPEC);
     * }</pre>
     * saltLength 取哈希长度 32（PSS 推荐，须写进接口契约），trailerField 固定 1（RFC 8017 唯一定义）；
     * JDK 默认 PSS 参数是主哈希 SHA-1 + MGF1(SHA-1) + saltLength 20，与多端不一致，故必须显式指定；
     * 启用时恢复 import java.security.spec.PSSParameterSpec
     */
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    /** 字符集 */
    private static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;
    /** 密钥长度 */
    private static final int KEY_SIZE = 2048;

    /**
     * RSA 公钥加密
     *
     * <p>公钥与明文均属可信输入，出错即自身故障，抛异常不降级
     *
     * @param publicKeyBase64 Base64 编码的 X.509 格式公钥
     * @param plainText       待加密明文
     * @return Base64 编码的密文
     * @throws IllegalArgumentException plainText 为 null 或空
     * @throws RuntimeException         公钥不可用或加密失败
     */
    public static String encrypt(String publicKeyBase64, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("RSA encryption requires non-empty plainText");
        }
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("RSA encryption failed [transformation=" + TRANSFORMATION + "]", e);
        }
    }

    /**
     * RSA 私钥解密
     *
     * <p>密文为空不降级返回空串：那等于把"解密失败"伪装成"明文就是空"，残缺请求会流到离根因最远的下游
     *
     * @param privateKeyBase64 Base64 编码的 PKCS#8 格式私钥
     * @param cipherTextBase64 Base64 编码的密文
     * @return 明文
     * @throws IllegalArgumentException cipherTextBase64 为 null 或空
     * @throws RuntimeException         私钥不可用或解密失败
     */
    public static String decrypt(String privateKeyBase64, String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            throw new IllegalArgumentException("RSA decryption requires non-empty cipherText");
        }
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC);

            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decryptedBytes = cipher.doFinal(cipherBytes);
            return new String(decryptedBytes, CHARSET);

        } catch (Exception e) {
            throw new RuntimeException("RSA decryption failed [transformation=" + TRANSFORMATION + "]", e);
        }
    }

    /**
     * RSA 私钥签名（SHA256withRSA）
     *
     * <p>私钥与待签明文均属可信输入（配置 / 服务端自拼的签名基串），出错即自身故障，抛异常不降级
     *
     * @param privateKeyBase64 Base64 编码的 PKCS#8 格式私钥
     * @param plainText        待签名内容
     * @return Base64 编码的签名
     * @throws IllegalArgumentException plainText 为 null 或空
     * @throws RuntimeException         私钥不可用或签名失败
     */
    public static String sign(String privateKeyBase64, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("RSA signing requires non-empty plainText");
        }
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(plainText.getBytes(CHARSET));

            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);

        } catch (Exception e) {
            throw new RuntimeException("RSA signing failed [algorithm=" + SIGNATURE_ALGORITHM + "]", e);
        }
    }

    /**
     * RSA 公钥验签（SHA256withRSA）
     *
     * <p>验签是谓词，"不通过"是合法输出：签名值属不可信输入，Base64 畸形、长度不符一律返回 false，
     * 与"签名无效"同等处理，避免畸形输入触发异常栈与 500
     *
     * <p>公钥与算法属可信输入，解析失败或算法不可用即配置故障，抛异常而非静默返回 false，
     * 否则会把"公钥配错"伪装成"所有应用签名都无效"
     *
     * @param publicKeyBase64 Base64 编码的 X.509 格式公钥
     * @param plainText       原始内容
     * @param signBase64      Base64 编码的签名
     * @return 是否通过
     * @throws IllegalStateException 公钥无法解析或算法不可用
     */
    public static boolean verify(String publicKeyBase64, String plainText, String signBase64) {
        if (plainText == null || plainText.isEmpty() || signBase64 == null || signBase64.isEmpty()) {
            return false;
        }
        byte[] signBytes;
        try {
            signBytes = Base64.getDecoder().decode(signBase64);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(plainText.getBytes(CHARSET));

            return signature.verify(signBytes);
        } catch (SignatureException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA signature verification failed [algorithm=" + SIGNATURE_ALGORITHM + "]", e);
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
            throw new RuntimeException("Failed to generate key pair [keySize=" + KEY_SIZE + "]", e);
        }
    }

    // ==================== 密钥加载（内部复用）====================

    private static PublicKey loadPublicKey(String publicKeyBase64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePublic(spec);
    }

    private static PrivateKey loadPrivateKey(String privateKeyBase64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePrivate(spec);
    }

}
