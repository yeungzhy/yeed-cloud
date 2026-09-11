package com.yeungzhy.yeed.gateway.filter.openapi;

import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import com.yeungzhy.yeed.gateway.config.ApiSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * nonce 防重放守卫（Redis SET NX EX 原子占用）
 *
 * <p>Redis 异常一律 fail-closed 拒绝而非放行，避免防重放静默失效
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
@Component
public class OpenapiReplayGuard {

    private final RedissonClient redissonClient;

    private final ApiSecurityProperties properties;

    public OpenapiReplayGuard(RedissonClient redissonClient, ApiSecurityProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    /**
     * 占用 nonce，成功才放行
     *
     * @param appId 应用标识（C 端为空串）
     * @param nonce 请求 nonce
     * @return 占用成功为空完成，重复或服务不可用为协议错误信号
     */
    public Mono<Void> occupy(String appId, String nonce) {
        String nonceKey = CacheConstant.OPENAPI_SIGN_NONCE_PREFIX + appId + ":" + nonce;
        RBucket<Object> bucket = redissonClient.getBucket(nonceKey);
        RFuture<Boolean> future = bucket.setIfAbsentAsync("1", properties.getNonceTtl());
        return Mono.<Boolean>create(sink -> future.whenComplete((occupied, error) -> {
                    if (error != null) {
                        sink.error(error);
                    } else {
                        sink.success(occupied);
                    }
                }))
                .onErrorResume(e -> {
                    log.error("OpenApi nonce 去重（Redis）异常，fail-closed 拒绝 appId={}, nonce={}", appId, nonce, e);
                    return Mono.error(new ApiSecurityRejectException(HttpStatus.FORBIDDEN,
                            ApiSecurityProtocol.CODE_NONCE_REPLAY, "防重放服务暂不可用，请更换 nonce 重试"));
                })
                .flatMap(occupied -> {
                    if (!Boolean.TRUE.equals(occupied)) {
                        return Mono.error(new ApiSecurityRejectException(HttpStatus.FORBIDDEN,
                                ApiSecurityProtocol.CODE_NONCE_REPLAY, "该 nonce 已提交过，请更换后重试"));
                    }
                    return Mono.empty();
                });
    }
}
