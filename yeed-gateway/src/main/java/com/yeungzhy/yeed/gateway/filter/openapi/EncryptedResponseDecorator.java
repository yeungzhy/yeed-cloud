package com.yeungzhy.yeed.gateway.filter.openapi;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Mono;

/**
 * 响应加密装饰器：把下游明文 body 整体加密后写回客户端，并回写协议版本头
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
public class EncryptedResponseDecorator extends ServerHttpResponseDecorator {

    private final OpenapiCryptoCodec codec;

    private final byte[] sessionKey;

    private final byte[] responseAad;

    /**
     * 响应加密装饰器
     *
     * @param delegate 原始响应
     * @param codec 报文加解密器
     * @param sessionKey 会话 AES key（32B）
     * @param responseAad 响应 AAD 字节
     */
    public EncryptedResponseDecorator(ServerHttpResponse delegate, OpenapiCryptoCodec codec,
                                      byte[] sessionKey, byte[] responseAad) {
        super(delegate);
        this.codec = codec;
        this.sessionKey = sessionKey;
        this.responseAad = responseAad;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return DataBufferUtils.join(body)
                .flatMap(dataBuffer -> {
                    byte[] plainText = DataBufferReadUtil.readAndRelease(dataBuffer);
                    if (plainText.length == 0) {
                        // 空响应（204/304 等）：无 body 可加密，直通且不追加加密头
                        return Mono.empty();
                    }
                    byte[] cipherText;
                    try {
                        cipherText = codec.encrypt(sessionKey, plainText, responseAad);
                    } catch (RuntimeException e) {
                        log.error("OpenApi 响应加密失败，终止连接（不回写部分密文）", e);
                        return Mono.error(e);
                    }
                    getDelegate().getHeaders().set(ApiSecurityProtocol.HDR_ENC_VERSION, ApiSecurityProtocol.VERSION);
                    getDelegate().getHeaders().setContentLength(cipherText.length);
                    DataBuffer out = getDelegate().bufferFactory().wrap(cipherText);
                    return super.writeWith(Mono.just(out));
                });
    }
}
