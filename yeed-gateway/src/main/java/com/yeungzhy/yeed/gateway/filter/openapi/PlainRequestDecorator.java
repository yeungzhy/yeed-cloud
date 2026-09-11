package com.yeungzhy.yeed.gateway.filter.openapi;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 明文请求装饰器：换装为剥除加密头、body 为明文的请求
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public class PlainRequestDecorator extends ServerHttpRequestDecorator {

    private final HttpHeaders headers;

    private final byte[] body;

    private final DataBufferFactory bufferFactory;

    /**
     * 明文请求装饰器
     *
     * @param delegate 原始请求
     * @param headers 转发头
     * @param body 明文 body
     * @param bufferFactory 缓冲区工厂
     */
    public PlainRequestDecorator(ServerHttpRequest delegate, HttpHeaders headers,
                                 byte[] body, DataBufferFactory bufferFactory) {
        super(delegate);
        this.headers = headers;
        this.body = body;
        this.bufferFactory = bufferFactory;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Flux<DataBuffer> getBody() {
        if (body.length == 0) {
            return Flux.empty();
        }
        // 每次订阅生成新包装（下游日志过滤器等可能多次订阅同一请求体）
        return Flux.defer(() -> Mono.just(bufferFactory.wrap(body)));
    }
}
