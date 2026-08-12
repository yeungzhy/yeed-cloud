package com.yeungzhy.yeed.common.core.crypto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 统一加解密配置项，集中管理 RSA、AES、HMAC 密钥配置
 *
 * <p>配置示例：
 * <pre>
 * crypto:
 *   rsa:
 *     public-key: xxx
 *     private-key: xxx
 *   aes:
 *     key: xxx
 *   hmac:
 *     secret: xxx
 * </pre>
 *
 * @author YangZhaoHuang
 * @since 2026-06-04
 */
@Data
@Accessors(chain = true)
public class CryptoProperties {

    /** RSA 非对称密钥对 */
    @Setter(value = AccessLevel.PACKAGE)
    private RsaKeyPair rsa;

    /** AES 对称加密密钥 */
    @Setter(value = AccessLevel.PACKAGE)
    private AesKey aes;

    /** HMAC 配置 */
    @Setter(value = AccessLevel.PACKAGE)
    private Hmac hmac;

    /** RSA 密钥对 */
    @Data
    @Accessors(chain = true)
    public static class RsaKeyPair {
        private String publicKey;
        private String privateKey;
    }

    /** AES 密钥 */
    @Data
    @Accessors(chain = true)
    public static class AesKey {
        private String key;
    }

    /** HMAC 配置 */
    @Data
    @Accessors(chain = true)
    public static class Hmac {
        private String secret;
    }

}