package com.yeungzhy.yeed.openapi.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenApi 公网探针接口
 *
 * <p>仅用于验证网关 → 本服务的链路连通性（含 ApiSecurityFilter 加解密），
 * 正式公网业务落地后按业务拆分为独立 Controller，本类随之删除
 *
 * @author yeungzhy
 * @since 2026-09-09
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/openapi")
public class OpenapiProbeController {

    /**
     * 连通性测试
     *
     * @return 探针回包
     * @author yeungzhy
     * @since 2026-09-09
     */
    @GetMapping("test")
    public String test() {
        log.info("openapi 公网探针被调用");
        return "openapi test ok";
    }


}
