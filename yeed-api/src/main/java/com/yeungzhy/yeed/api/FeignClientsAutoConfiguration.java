package com.yeungzhy.yeed.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Feign 客户端集中注册入口（自动配置类）。
 *
 * <p>本类位于 yeed-api 模块，通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 被 Spring Boot 自动装配机制加载。任何依赖 yeed-api 的服务只要 classpath 引入了本模块，
 * 即可自动获得所有 {@code @FeignClient} 接口的代理 Bean，无需在各自启动类上额外标注
 * {@link EnableFeignClients}。
 *
 * <p>装配边界：
 * <ul>
 *   <li>{@link EnableFeignClients}：扫描并注册根包下全部 {@code @FeignClient} 代理 Bean
 *       （扫描范围统一为 {@code com.yeungzhy.yeed.api}，新增契约无需改动本类）；</li>
 *   <li>各内部契约客户端通过 {@code @FeignClient(configuration = ...)} 显式引用
 *       {@link com.yeungzhy.yeed.api.feign.config.InternalFeignConfig InternalFeignConfig}
 *       获得 token 透传与错误解码，不再做任何全局 Bean 注册。</li>
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
