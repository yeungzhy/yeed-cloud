package com.yeungzhy.yeed.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Feign 客户端集中注册入口（自动配置类）
 *
 * <p>由 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 加载，
 * 引入 yeed-api 的服务自动获得全部 {@code @FeignClient} 代理 Bean，不必在启动类上再标
 * {@link EnableFeignClients}
 *
 * <p>装配边界：
 * <ul>
 *   <li>{@link EnableFeignClients} 扫描范围固定为 {@code com.yeungzhy.yeed.api}，新增契约无需改本类
 *   <li>token 透传与错误解码由各内部契约客户端用 {@code @FeignClient(configuration = ...)} 显式引用
 *       {@link com.yeungzhy.yeed.api.feign.config.InternalFeignConfig InternalFeignConfig}，
 *       不做全局 Bean 注册，否则会波及其余 Feign 客户端
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 * @see com.yeungzhy.yeed.api.feign.config.InternalFeignConfig 内部契约专用装配
 */
@AutoConfiguration
@EnableFeignClients("com.yeungzhy.yeed.api")
public class FeignClientsAutoConfiguration {

}
