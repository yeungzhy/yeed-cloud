package com.yeungzhy.yeed.api.feign.config;

import com.yeungzhy.yeed.api.feign.decoder.InternalErrorDecoder;
import com.yeungzhy.yeed.api.feign.interceptor.InternalTokenRelayInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * 内部 Feign 契约的 <b>FeignClient 级装配</b>（被 {@code @FeignClient(configuration = ...)} 引用）。
 *
 * <p><b>不是 Spring 配置类</b>：不要标注 {@code @Component} / {@code @Configuration}，也不要让它进入
 * 组件扫描（本类故意不写 {@code @Configuration}）——它只应被实例化进引用它的各 Feign 子容器。
 * 项目里 {@code common-web/config} 下的 {@code @AutoConfiguration} 是另一回事，勿类比。
 *
 * <p>装配的两个扩展点同属"内部契约"语义（{@code Internal} 前缀），按 Feign 扩展点类型分包：
 * {@link InternalTokenRelayInterceptor}（{@code feign.interceptor}）、
 * {@link InternalErrorDecoder}（{@code feign.decoder}）；本包只留装配入口。
 *
 * @author yeungzhy
 */
public class InternalFeignConfig {

    /** token 透传拦截器（Bean 级：仅内部契约客户端使用） */
    @Bean
    public InternalTokenRelayInterceptor internalTokenRelayInterceptor() {
        return new InternalTokenRelayInterceptor();
    }

    /** 内部契约错误解码：下游 HTTP 错误 → BizException（码 + 话术透传） */
    @Bean
    public InternalErrorDecoder internalErrorDecoder() {
        return new InternalErrorDecoder();
    }

}
