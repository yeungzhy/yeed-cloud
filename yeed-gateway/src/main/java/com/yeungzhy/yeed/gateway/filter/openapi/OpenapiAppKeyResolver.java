package com.yeungzhy.yeed.gateway.filter.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import com.yeungzhy.yeed.common.core.crypto.RsaUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接入应用公钥读取与 B2B 验签（公钥缓存只读方）
 *
 * <p>缓存由 yeed-openapi 模块写入（主备两把公钥与上一把过期时间），本类只读：
 * 未命中即视为 appId 不存在或已停用；验签先主后备，两把都失败统一 401，不向客户端区分失败原因
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
@Component
public class OpenapiAppKeyResolver {

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    public OpenapiAppKeyResolver(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 B2B 请求签名（C 端请求不带 appId，直接放行）
     *
     * @param header 已解析的加密头
     * @param cipher 密文请求体（待签基串要含其摘要）
     * @return 通过为空完成，否则协议错误信号
     */
    public Mono<Void> verify(EncryptedRequestHeader header, byte[] cipher) {
        if (!header.b2b()) {
            return Mono.empty();
        }
        String signBase = buildSignBase(header, cipher);
        return readAppPublicKey(header.appId())
                .flatMap(pubKey -> {
                    if (verifySignature(pubKey, signBase, header.sign())) {
                        return Mono.empty();
                    }
                    log.warn("OpenApi 验签失败 appId={}", header.appId());
                    return Mono.error(signRejected("签名校验失败"));
                });
    }

    /**
     * 主备公钥验签：先验主，未过再验上一把（须仍在过渡期内）
     *
     * @param pubKey 应用公钥（含上一把，可为空）
     * @param signBase 待签基串
     * @param sign 客户端签名（Base64）
     * @return true=通过；公钥解析失败抛 IllegalStateException（缓存数据异常，冒泡为 500）
     */
    private boolean verifySignature(AppPublicKey pubKey, String signBase, String sign) {
        if (RsaUtil.verify(pubKey.publicKey(), signBase, sign)) {
            return true;
        }
        return StringUtils.hasText(pubKey.publicKeyPrev())
                && pubKey.prevKeyExpireMillis() != null
                && pubKey.prevKeyExpireMillis() > System.currentTimeMillis()
                && RsaUtil.verify(pubKey.publicKeyPrev(), signBase, sign);
    }

    /**
     * 读应用公钥缓存
     *
     * @param appId 应用标识
     * @return 应用公钥，未命中或读取异常为协议错误信号
     */
    private Mono<AppPublicKey> readAppPublicKey(String appId) {
        String key = CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + appId;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        RFuture<Object> future = bucket.getAsync();
        return Mono.<Object>create(sink -> future.whenComplete((value, error) -> {
                    if (error != null) {
                        sink.error(error);
                    } else {
                        sink.success(value);
                    }
                }))
                .onErrorResume(e -> {
                    log.error("OpenApi 公钥缓存读取异常，fail-closed 拒绝 appId={}", appId, e);
                    return Mono.error(signRejected("appId 不存在或已停用"));
                })
                .map(value -> parseAppPublicKey(value, appId));
    }

    /**
     * 解析公钥缓存值（兼容 Map 与 JSON 字符串两种存放形态）
     *
     * @param value Redis 原始值
     * @param appId 应用标识（仅用于告警日志）
     * @return 应用公钥模型
     */
    @SuppressWarnings("unchecked")
    private AppPublicKey parseAppPublicKey(Object value, String appId) {
        Map<String, Object> map;
        if (value instanceof Map) {
            map = (Map<String, Object>) value;
        } else if (value instanceof String json) {
            try {
                map = objectMapper.readValue(json, LinkedHashMap.class);
            } catch (Exception e) {
                log.error("OpenApi 公钥缓存 JSON 解析失败 appId={}", appId, e);
                throw signRejected("appId 不存在或已停用");
            }
        } else {
            map = null;
        }
        String publicKey = map == null ? null : toStringValue(map.get("publicKey"));
        if (!StringUtils.hasText(publicKey)) {
            log.warn("OpenApi 公钥缓存缺失或为空 appId={}", appId);
            throw signRejected("appId 不存在或已停用");
        }
        return new AppPublicKey(publicKey,
                toStringValue(map.get("publicKeyPrev")),
                toLongValue(map.get("prevKeyExpireTime")));
    }

    /**
     * 构造统一 401 验签错误（不向客户端区分具体失败原因）
     *
     * @param msg 对外错误消息
     * @return 协议错误信号
     */
    private static ApiSecurityRejectException signRejected(String msg) {
        return new ApiSecurityRejectException(HttpStatus.UNAUTHORIZED, ApiSecurityProtocol.CODE_SIGN_INVALID, msg);
    }

    /**
     * 对象安全转字符串
     *
     * @param value 原始值
     * @return 字符串，null 为 null
     */
    private static String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 对象安全转长整型（epoch 毫秒）
     *
     * @param value 原始值（Number 或数字字符串）
     * @return 值，null / 不可解析为 null
     */
    private static Long toLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构造 B2B 待签基串
     *
     * <p>格式：{@code method\npath\nappId\ntimestamp\nnonce\nBase64(SHA-256(cipherBody))}
     *
     * <p>用换行分隔而非竖线：基串是对外契约，客户端须照文档或 SDK 自行拼接，换行是签名基串的通行写法，
     * 且 HTTP 方法、路径与头值都不允许 CR/LF，字段值内出现不了分隔符，字段边界无法伪造
     *
     * @param header 已解析的加密头
     * @param cipher 密文请求体
     * @return 待签基串
     */
    private static String buildSignBase(EncryptedRequestHeader header, byte[] cipher) {
        String bodyDigest = Base64.getEncoder().encodeToString(DigestUtils.sha256(cipher));
        return  """
                %s
                %s
                %s
                %s
                %s
                %s""".formatted(header.method(), header.path(), header.appId(),
                header.timestamp(), header.nonce(), bodyDigest);
    }


    /**
     * 应用注册公钥模型（可能含轮换过渡期的上一把）
     *
     * @param publicKey 主公钥
     * @param publicKeyPrev 上一把公钥（可为 null）
     * @param prevKeyExpireMillis 上一把过期时间（epoch 毫秒，可为 null）
     */
    private record AppPublicKey(String publicKey, String publicKeyPrev, Long prevKeyExpireMillis) { }

}
