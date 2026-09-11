package com.yeungzhy.yeed.openapi.bootstrap;

import com.yeungzhy.yeed.openapi.app.cache.OpenapiAppPubKeyCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * OpenApi 公钥缓存预热器（每次启动必做）
 *
 * <p> 网关验签只读缓存且未命中即 401，服务重启后若 Redis 已清空，须由本预热器把
 * 全部启用应用的公钥重新写入
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
@Component
public class OpenapiAppPubKeyCachePreloader implements ApplicationRunner {

    @Resource
    private OpenapiAppPubKeyCache openapiAppPubKeyCache;

    /**
     * 启动时预载全部启用应用
     *
     * <p> Redis 不可用时不阻断启动：RedisHelper 已把异常收敛为 false，缓存缺失由
     * 应用变更时的重写与下次启动预载补齐
     *
     * @param args 启动参数，本预热器不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        openapiAppPubKeyCache.preload();
    }

}
