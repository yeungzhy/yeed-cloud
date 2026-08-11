package com.yeungzhy.yeed.api;

import com.yeungzhy.yeed.api.feign.EnableFeignFallbacks;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端集中注册入口。
 *
 * <p>本类位于 yeed-api 模块，通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 被 Spring Boot 自动装配机制加载。任何依赖 yeed-api 的服务只要 classpath 引入了本模块，
 * 即可自动获得所有 {@code @FeignClient} 接口的代理 Bean，无需在各自启动类上额外标注 {@code @EnableFeignClients}。
 *
 * <p>扫描范围限定为 {@code com.yeungzhy.yeed.api}：所有跨服务 Feign 契约统一收纳于此根包下的 {@code *.feign} 子包，
 * 新增 Feign 客户端时只需遵循该约定，无需修改本类或任何消费方启动类。
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Configuration
@EnableFeignFallbacks
@EnableFeignClients("com.yeungzhy.yeed.api")
public class FeignClientsAutoConfiguration { }
