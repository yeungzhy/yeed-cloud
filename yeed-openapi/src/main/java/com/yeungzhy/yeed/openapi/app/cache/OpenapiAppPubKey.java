package com.yeungzhy.yeed.openapi.app.cache;

/**
 * OpenApi 应用公钥缓存值
 *
 * <p> 字段名即缓存 JSON 的键名，网关按同名键读取，改名会静默打断验签
 *
 * @param publicKey 当前主 RSA 公钥（Base64(DER)）
 * @param publicKeyPrev 上一把 RSA 公钥，无轮换过渡期为 null
 * @param prevKeyExpireTime 上一把公钥失效时刻（epoch 毫秒），无过渡期为 null
 * @author yeungzhy
 * @since 2026-09-10
 */
public record OpenapiAppPubKey(String publicKey, String publicKeyPrev, Long prevKeyExpireTime) {
}
