package com.yeungzhy.yeed.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES 工具类 - JDK 标准实现，约定参数（方便多端统一）：
 * <li>Algorithm: AES</li>
 * <li>Mode: GCM</li>
 * <li>Padding: NoPadding（GCM 不需要填充）</li>
 * <li>Key Size: 256 bit</li>
 * <li>IV Length: 12 byte (96 bit)</li>
 * <li>Tag Length: 128 bit (16 byte)</li>
 * <li>Charset: UTF-8</li>
 * <li>密文格式: Base64( IV(12字节) + ciphertext + tag(16字节) )</li>
 */
public class AesUtil {
    private AesUtil() {}
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 算法 */
    private static final String ALGORITHM = "AES";
    /** 模式 */
    private static final String MODE = "GCM";
    /** 填充 */
    private static final String PADDING = "NoPadding";
    /** 完整 Transformation */
    private static final String TRANSFORMATION = ALGORITHM + "/" + MODE + "/" + PADDING;
    /** 密钥长度 */
    private static final int KEY_SIZE = 256;
    /** IV 长度 */
    private static final int IV_LENGTH = 12;
    /** GCM Tag 长度 */
    private static final int TAG_LENGTH = 128;
    /** 字符集 */
    private static final Charset CHARSET = StandardCharsets.UTF_8;


    // ==================== 密码派生参数（PBKDF2）====================
    /** 密钥派生算法 */
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    /** Salt 长度 */
    private static final int SALT_LENGTH = 16;
    /** 迭代次数 */
    private static final int ITERATION_COUNT = 100000;
    /** 派生密钥长度 */
    private static final int DERIVED_KEY_LENGTH = 256;


    /**
     * AES 加密（预共享密钥）
     *
     * @param keyBase64 Base64 编码的 AES 密钥（256 bit）
     * @param plainText 明文
     * @return Base64 编码的密文（IV + ciphertext + tag）
     */
    public static String encrypt(String keyBase64, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = loadKey(keyBase64);
            byte[] iv = generateIv();

            byte[] cipherBytes = doEncrypt(key, iv, plainText.getBytes(CHARSET));

            // 拼接: IV(12) + cipherBytes(含tag)
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败 [transformation=" + TRANSFORMATION + "]", e);
        }
    }

    /**
     * AES 解密（预共享密钥）
     *
     * @param keyBase64        Base64 编码的 AES 密钥
     * @param cipherTextBase64 Base64 编码的密文（IV + ciphertext + tag）
     * @return 明文
     */
    public static String decrypt(String keyBase64, String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = loadKey(keyBase64);

            byte[] fullBytes = Base64.getDecoder().decode(cipherTextBase64);

            // 截取 IV
            ByteBuffer buffer = ByteBuffer.wrap(fullBytes);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            // 剩余为 ciphertext + tag
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            byte[] decrypted = doDecrypt(key, iv, cipherBytes);
            return new String(decrypted, CHARSET);

        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败 [transformation=" + TRANSFORMATION + "]", e);
        }
    }


    // ==================== 2. 密码派生密钥模式（PBKDF2）====================

    /**
     * AES 加密（密码派生密钥）
     *
     * @param password  用户密码（明文）
     * @param plainText 明文
     * @return Base64 编码的密文（salt + IV + ciphertext + tag）
     */
    public static String encryptByPassword(String password, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        try {
            // 1. 随机生成 Salt
            byte[] salt = generateSalt();

            // 2. 派生密钥
            SecretKey key = deriveKey(password, salt);

            // 3. 随机生成 IV
            byte[] iv = generateIv();

            // 4. 加密
            byte[] cipherBytes = doEncrypt(key, iv, plainText.getBytes(CHARSET));

            // 5. 拼接: salt(16) + IV(12) + cipherBytes(含tag)
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + cipherBytes.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(cipherBytes);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new RuntimeException(
                    "AES 加密失败 [kdf=" + KDF_ALGORITHM + ", iterations=" + ITERATION_COUNT + "]", e);
        }
    }


    /**
     * AES 解密（密码派生密钥）
     *
     * @param password         用户密码（明文）
     * @param cipherTextBase64 Base64 编码的密文（salt + IV + ciphertext + tag）
     * @return 明文
     */
    public static String decryptByPassword(String password, String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return "";
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        try {
            byte[] fullBytes = Base64.getDecoder().decode(cipherTextBase64);

            // 1. 截取 Salt
            ByteBuffer buffer = ByteBuffer.wrap(fullBytes);
            byte[] salt = new byte[SALT_LENGTH];
            buffer.get(salt);

            // 2. 截取 IV
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            // 3. 剩余为 ciphertext + tag
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            // 4. 用相同密码和 Salt 重新派生密钥
            SecretKey key = deriveKey(password, salt);

            // 5. 解密
            byte[] decrypted = doDecrypt(key, iv, cipherBytes);
            return new String(decrypted, CHARSET);

        } catch (Exception e) {
            throw new RuntimeException(
                    "AES 解密失败 [kdf=" + KDF_ALGORITHM + ", iterations=" + ITERATION_COUNT + "]", e);
        }
    }




    /**
     * 生成 AES 密钥（256 bit）
     *
     * @return Base64 编码的密钥
     */
    public static String generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(KEY_SIZE, SECURE_RANDOM);
            SecretKey key = generator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("生成 AES 密钥失败 [keySize=" + KEY_SIZE + "]", e);
        }
    }


    // ==================== 内部方法 ====================

    /**
     * 执行加密（核心逻辑，两种模式复用）
     */
    private static byte[] doEncrypt(SecretKey key, byte[] iv, byte[] plainBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plainBytes);
    }

    /**
     * 执行解密（核心逻辑，两种模式复用）
     */
    private static byte[] doDecrypt(SecretKey key, byte[] iv, byte[] cipherBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(cipherBytes);
    }


    /**
     * PBKDF2 密钥派生
     */
    private static SecretKey deriveKey(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, DERIVED_KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    private static SecretKey loadKey(String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

}
