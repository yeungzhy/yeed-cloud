package com.yeungzhy.yeed.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.gateway.config.ApiSecurityExcludeProperties;
import com.yeungzhy.yeed.gateway.config.ApiSecurityProperties;
import com.yeungzhy.yeed.gateway.filter.openapi.ApiSecurityProtocol;
import com.yeungzhy.yeed.gateway.filter.openapi.ApiSecurityRejectException;
import com.yeungzhy.yeed.gateway.filter.openapi.DataBufferReadUtil;
import com.yeungzhy.yeed.gateway.filter.openapi.EncryptedRequestHeader;
import com.yeungzhy.yeed.gateway.filter.openapi.EncryptedResponseDecorator;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiAppKeyResolver;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiCryptoCodec;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiReplayGuard;
import com.yeungzhy.yeed.gateway.filter.openapi.PlainRequestDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenApi 报文加解密唯一安全过滤器（公网面统一验签、解密、防重放、响应加密）
 *
 * <p>仅处理「公网加密前缀」内的请求（默认 {@code /openapi/**}），其余路径直通放行；
 * 前缀内仍命中豁免名单（见 {@link ApiSecurityExcludeProperties}）的端点也明文直通
 *
 * <p>执行顺序（对应 openapi-security-design.md §6.2/§6.3，编号为设计原文步骤号）：
 * <ol>
 *   <li>校验必填加密头（X-Enc-Version / X-Enc-Alg / X-Enc-Key / X-Timestamp / X-Nonce），B2B 还必填 X-App-Id / X-Sign</li>
 *   <li>校验 Content-Length：缺省 / 超上限直接拒绝，避免先缓冲超限密文</li>
 *   <li>校验 timestamp 与服务器时间偏差（默认 1 分钟）</li>
 *   <li>nonce 去重：SET NX EX 原子占用，重复即拒绝（防重放）</li>
 *   <li>B2B 验签：用应用注册公钥（Redis 缓存，主备两把）验 X-Sign</li>
 *   <li>RSA 私钥解出会话 AES key（客户端用平台公钥加密传输）</li>
 *   <li>AES-256-GCM 解包体密文（AAD 绑定 {@code appId|method|path|timestamp|nonce}）</li>
 *   <li>明文请求继续下传（剥除加密头、改写 Content-Length），响应反向加密后回写客户端</li>
 * </ol>
 *
 * <p>协议常量、加密头解析、应用公钥验签、报文加解密、防重放与请求响应装饰器
 * 均在 {@code com.yeungzhy.yeed.gateway.filter.openapi} 子包内，本类只做编排与错误响应
 *
 * <p>错误处理（对应 §6.5/§6.6）：
 * <ul>
 *   <li>协议类错误一律明文响应（不带 X-Enc-Version），HTTP 状态码为真值，body 为 {@code {"code":..,"msg":"..","data":null}}</li>
 *   <li>密文解密失败不区分具体原因，统一 400「请求格式非法」，避免被探测</li>
 *   <li>配置类故障（私钥缺失/非法、公钥缓存损坏等）不在此吞掉，向上冒泡由全局异常处理兜底为 500</li>
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Slf4j
public class ApiSecurityFilter implements GlobalFilter, Ordered {

    /** 本过滤器排序：早于访问日志过滤器（+200），确保日志看到的是解密后的明文 */

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final int order;

    private final ApiSecurityProperties properties;

    private final ApiSecurityExcludeProperties excludeProperties;

    private final OpenapiReplayGuard replayGuard;

    private final OpenapiAppKeyResolver appKeyResolver;

    private final OpenapiCryptoCodec cryptoCodec;

    private final ObjectMapper objectMapper;

    public ApiSecurityFilter(int order,
                             ApiSecurityProperties properties,
                             ApiSecurityExcludeProperties excludeProperties,
                             OpenapiReplayGuard replayGuard,
                             OpenapiAppKeyResolver appKeyResolver,
                             OpenapiCryptoCodec cryptoCodec,
                             ObjectMapper objectMapper) {
        this.order = order;
        this.properties = properties;
        this.excludeProperties = excludeProperties;
        this.replayGuard = replayGuard;
        this.appKeyResolver = appKeyResolver;
        this.cryptoCodec = cryptoCodec;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 1. 范围判定：总开关 / 公网前缀 / 豁免名单，任一不满足即直通
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        if (!matchesPublicPath(request)) {
            return chain.filter(exchange);
        }
        if (excludeProperties.isExcluded(request)) {
            return chain.filter(exchange);
        }
        return processEncryptedRequest(exchange, chain);
    }

    /**
     * 校验请求路径是否命中公网加密前缀
     *
     * @param request 当前请求
     * @return true=命中
     */
    private boolean matchesPublicPath(ServerHttpRequest request) {
        String path = request.getPath().value();
        for (String prefix : properties.getPublicPrefixes()) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    // ==================== 加解密流水线 ====================

    /**
     * 完整加解密处理流水线
     *
     * <p>全程以 {@link ApiSecurityRejectException} 表达"该回明文协议错误"，在管道末端统一落响应，
     * 防止中间步骤各自 writeWith 造成响应被写两次
     */
    private Mono<Void> processEncryptedRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 同步校验都放 Mono.defer 里执行：defer 供应商抛出的异常会被转为错误信号，
        // 由下方 onErrorResume 统一转明文响应（否则会在过滤链外抛给全局异常处理器变 500）
        return Mono.defer(() -> {
            ServerHttpRequest request = exchange.getRequest();
            EncryptedRequestHeader header = EncryptedRequestHeader.parse(request);  // 必填头完整性 + 版本/算法 + appId/验签头
            boolean emptyBody = resolveBodyExpectation(request);                    // 头部长度形态预检
            long contentLength = resolveContentLength(request);

            // timestamp 偏差校验（先于 nonce 占用，避免攻击者用过期请求污染 nonce 空间）
            if (Math.abs(System.currentTimeMillis() - header.timestamp()) > properties.getAllowDisparity().toMillis()) {
                throw new ApiSecurityRejectException(HttpStatus.UNAUTHORIZED, ApiSecurityProtocol.CODE_TIMESTAMP_SKEW,
                        "时间戳偏差过大或已过期");
            }

            DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
            return readCipherBody(request, contentLength, emptyBody)
                    .flatMap(cipher -> replayGuard.occupy(header.appId(), header.nonce()).thenReturn(cipher))
                    .flatMap(cipher -> appKeyResolver.verify(header, cipher).thenReturn(cipher))
                    .flatMap(cipher -> decryptAndForward(exchange, chain, header, cipher, bufferFactory))
                    .onErrorMap(DataBufferLimitException.class,
                            e -> new ApiSecurityRejectException(HttpStatus.PAYLOAD_TOO_LARGE,
                                    ApiSecurityProtocol.CODE_BODY_TOO_LARGE,
                                    "密文请求体超过上限：" + properties.getMaxEncryptedBodyBytes()));
        }).onErrorResume(ApiSecurityRejectException.class, e -> reject(exchange, e.status(), e.code(), e.msg()));
    }

    // ==================== 请求体预检 ====================

    /**
     * 请求体形态预检：返回密文是否为空
     *
     * @param request 当前请求
     * @return true=请求体为空（无密文可解）
     */
    private boolean resolveBodyExpectation(ServerHttpRequest request) {
        long contentLength = resolveContentLength(request);
        if (contentLength == 0) {
            return true;
        }
        if (contentLength < 0) {
            if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD) {
                return true;
            }
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST,
                    ApiSecurityProtocol.CODE_CONTENT_LENGTH_MISSING,
                    "加密请求必须携带 Content-Length（不接受 chunked）");
        }
        if (contentLength > properties.getMaxEncryptedBodyBytes().toBytes()) {
            throw new ApiSecurityRejectException(HttpStatus.PAYLOAD_TOO_LARGE,
                    ApiSecurityProtocol.CODE_BODY_TOO_LARGE,
                    "密文请求体超过上限：" + properties.getMaxEncryptedBodyBytes());
        }
        return false;
    }

    /**
     * 读取 Content-Length 头
     *
     * @param request 当前请求
     * @return 长度值，缺省或非法为 -1
     */
    private long resolveContentLength(ServerHttpRequest request) {
        String value = request.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (!StringUtils.hasText(value)) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 读取完整密文请求体（受上限保护）
     *
     * @param request 当前请求
     * @param contentLength Content-Length（已预检不超限）
     * @param emptyBody 是否空请求体
     * @return 密文字节
     */
    private Mono<byte[]> readCipherBody(ServerHttpRequest request, long contentLength, boolean emptyBody) {
        if (emptyBody || contentLength == 0) {
            return Mono.just(EMPTY_BYTES);
        }
        long limit = Math.min(properties.getMaxEncryptedBodyBytes().toBytes(), Integer.MAX_VALUE - 1);
        return DataBufferUtils.join(request.getBody(), (int) limit)
                .map(DataBufferReadUtil::readAndRelease);
    }

    // ==================== 解密转发 ====================

    /**
     * 解密并转发
     *
     * <p>密码学运算放 boundedElastic，避免 RSA/AES 阻塞 Netty event loop 线程
     *
     * @param exchange 当前交换
     * @param chain 过滤器链
     * @param header 已解析的加密头
     * @param cipher 已验证通过的密文请求体
     * @param bufferFactory 缓冲区工厂
     * @return 转发结果
     */
    private Mono<Void> decryptAndForward(ServerWebExchange exchange, GatewayFilterChain chain,
                                         EncryptedRequestHeader header, byte[] cipher,
                                         DataBufferFactory bufferFactory) {
        return Mono.fromCallable(() -> cryptoCodec.decrypt(header, cipher))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decrypted -> {
                    ServerHttpRequest request = exchange.getRequest();
                    HttpHeaders forwardHeaders = buildForwardHeaders(request, decrypted.plainText().length);
                    ServerHttpRequest forwardRequest = new PlainRequestDecorator(request, forwardHeaders,
                            decrypted.plainText(), bufferFactory);
                    ServerHttpResponse forwardResponse = new EncryptedResponseDecorator(exchange.getResponse(),
                            cryptoCodec, decrypted.sessionKey(), header.responseAad());
                    ServerWebExchange mutated = exchange.mutate()
                            .request(forwardRequest)
                            .response(forwardResponse)
                            .build();
                    return chain.filter(mutated);
                });
    }

    /**
     * 构造剥除加密头后的转发请求头
     *
     * @param request 原始请求
     * @param plainLength 明文长度
     * @return 转发头（含改写后的 Content-Length）
     */
    private HttpHeaders buildForwardHeaders(ServerHttpRequest request, int plainLength) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        for (String headerName : ApiSecurityProtocol.ENCRYPT_HEADERS) {
            headers.remove(headerName);
        }
        if (plainLength > 0) {
            headers.setContentLength(plainLength);
        } else {
            headers.remove(HttpHeaders.CONTENT_LENGTH);
        }
        return headers;
    }

    // ==================== 明文错误响应 ====================

    /**
     * 明文协议错误响应（不带 X-Enc-Version）
     *
     * @param exchange 当前交换
     * @param status HTTP 状态码
     * @param code body 业务码
     * @param msg 错误消息
     * @return 已完成写回的 Mono
     */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return response.setComplete();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Map.of 不接受 null 值，data 占位只能用可变 Map，否则错误体退化为空
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", code);
        errorBody.put("msg", msg);
        errorBody.put("data", null);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(errorBody);
        } catch (Exception e) {
            log.error("OpenApi 明文错误 JSON 序列化失败", e);
            return response.setComplete();
        }
        DataBuffer dataBuffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(dataBuffer));
    }
}
