package com.yeungzhy.yeed.openapi.app.cache;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yeungzhy.yeed.common.cache.support.RedisHelper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.openapi.app.entity.OpenapiApp;
import com.yeungzhy.yeed.openapi.app.mapper.OpenapiAppMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenApi 应用公钥缓存（唯一写入口）
 *
 * <p> 网关验签只读 {@code yeed:openapi:app:pubkey:{appId}}，本类负责该键的写入与清除：
 * 应用新增或更新后按主键回查重写、停用或删除后清键、启动时全量预载启用应用
 *
 * <p> 值永久存储不设 TTL：网关未命中即 401，而键只在应用变更与启动预载时重写，
 * 一旦过期且期间无变更，该应用会持续验签失败
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
@Component
public class OpenapiAppPubKeyCache {

    @Resource
    private OpenapiAppMapper openapiAppMapper;
    @Resource
    private RedisHelper redisHelper;


    /**
     * 按主键回查并刷新公钥缓存：启用即写，停用即清
     *
     * <p> 回查而非直接用入参实体，是为拿到落库后的最终状态与默认值
     *
     * @param id 应用主键
     */
    public void refresh(Long id) {
        if (Objects.isNull(id)) {
            return;
        }
        OpenapiApp entity = openapiAppMapper.selectById(id);
        if (Objects.isNull(entity)) {
            return;
        }
        if (EnableStatusEnum.isEnabled(entity.getStatus())) {
            write(entity);
        } else {
            evict(entity.getAppId());
        }
    }

    /**
     * 清除指定应用的公钥缓存
     *
     * @param appId 应用标识
     */
    public void evict(String appId) {
        if (!StringUtils.hasText(appId)) {
            return;
        }
        redisHelper.delete(CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + appId);
    }

    /**
     * 启动预载：全部启用应用一次批量写入
     *
     * <p> 只覆盖不先清空，避免预载途中网关读到空缓存而把已注册应用判为未注册
     */
    public void preload() {
        List<OpenapiApp> apps = openapiAppMapper.selectList(
                Wrappers.<OpenapiApp>lambdaQuery().eq(OpenapiApp::getStatus, EnableStatusEnum.ENABLED));
        Map<String, Object> batch = new LinkedHashMap<>(apps.size());
        for (OpenapiApp entity : apps) {
            if (StringUtils.hasText(entity.getAppId())) {
                batch.put(CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + entity.getAppId(), toCacheValue(entity));
            }
        }
        if (batch.isEmpty()) {
            log.info("OpenApi 公钥缓存预载跳过，无启用应用");
            return;
        }
        redisHelper.setBatch(batch, null);
        log.info("OpenApi 公钥缓存预载完成，应用数={}", batch.size());
    }

    /**
     * 写入单个应用的公钥缓存
     */
    private void write(OpenapiApp entity) {
        if (!StringUtils.hasText(entity.getAppId())) {
            return;
        }
        redisHelper.set(CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + entity.getAppId(), toCacheValue(entity), null);
    }

    /**
     * 实体转缓存值：上一把失效时刻换算为 epoch 毫秒，与网关的数值读取口径对齐
     */
    private OpenapiAppPubKey toCacheValue(OpenapiApp entity) {
        LocalDateTime expireTime = entity.getPrevKeyExpireTime();
        Long prevKeyExpireTime = Objects.isNull(expireTime)
                ? null
                : expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new OpenapiAppPubKey(entity.getPublicKey(), entity.getPublicKeyPrev(), prevKeyExpireTime);
    }

}
