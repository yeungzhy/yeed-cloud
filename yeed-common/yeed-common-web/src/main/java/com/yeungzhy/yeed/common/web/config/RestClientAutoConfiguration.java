package com.yeungzhy.yeed.common.web.config;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置：底层替换为 Apache HttpClient 5，手动构建带连接池/超时/重试/Keep-Alive 调优的
 * {@link CloseableHttpClient}，通过 {@link HttpComponentsClientHttpRequestFactory} 注入 {@link RestClient}。
 *
 * <p>Spring Boot 对 RestClient 的自动 requestFactory 仅套用默认池（每路由约 5 连接），生产高并发下
 * 连接排队、延迟飙升；手动调优后的 HttpClient 才是真正的高性能客户端。
 *
 * <p>OpenFeign 自管理其 hc5 客户端（由 spring.cloud.openfeign.httpclient.hc5.* 属性驱动），
 * 与此处的 {@link CloseableHttpClient} 互不干扰，各自独立连接池。
 *
 * @author yeungzhy
 * @since 2026-08-07
 */
@AutoConfiguration
@ConditionalOnClass({RestClient.class, CloseableHttpClient.class})
public class RestClientAutoConfiguration {

    // ===== 连接池参数 =====
    /** 连接池总连接数 */
    private static final int MAX_CONN_TOTAL = 200;
    /** 单域名(路由)最大连接数 */
    private static final int MAX_CONN_PER_ROUTE = 50;

    // ===== 超时参数（秒）=====
    /** 建立连接超时 */
    private static final int CONNECT_TIMEOUT = 5;
    /** 等待响应(读)超时 */
    private static final int RESPONSE_TIMEOUT = 30;
    /** 从连接池获取连接的等待超时 */
    private static final int CONNECTION_REQUEST_TIMEOUT = 3;

    // ===== Keep-Alive / 重试 =====
    /** 默认连接 Keep-Alive 时长（秒），服务端未指定时使用 */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 30;
    /** 空闲连接驱逐周期（秒），配合 evictIdleConnections 清理泄漏连接 */
    private static final int EVICT_IDLE_SECONDS = 60;
    /** 失败重试最大次数（仅幂等方法 GET/HEAD 等） */
    private static final int RETRY_MAX_ATTEMPTS = 3;
    /** 重试间隔（秒） */
    private static final int RETRY_INTERVAL_SECONDS = 1;

    /**
     * 调优后的 Apache HttpClient 5：连接池 + 三类超时 + 重试策略 + Keep-Alive 策略 + 过期/空闲连接驱逐。
     * 业务侧可声明同名 Bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(CloseableHttpClient.class)
    public CloseableHttpClient closeableHttpClient() {
        // 连接超时：配置到连接管理器的 ConnectionConfig
        // （RequestConfig.setConnectTimeout 在 httpclient5 5.5 已弃用，改用 ConnectionConfig.Builder#setConnectTimeout）
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT))
                .build();

        // 连接池
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(MAX_CONN_TOTAL)
                .setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        // 超时：响应超时 + 从连接池获取连接超时（connect 超时已上移至 ConnectionConfig）
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT))
                .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECTION_REQUEST_TIMEOUT))
                .build();

        // Keep-Alive 策略：优先按响应头 Keep-Alive: timeout=N，否则用默认值
        ConnectionKeepAliveStrategy keepAliveStrategy = (response, context) -> {
            for (Header header : response.getHeaders("Keep-Alive")) {
                String value = header.getValue();
                if (value == null) {
                    continue;
                }
                // 解析形如 "timeout=30, max=100" 的头值
                for (String token : value.split(",")) {
                    String[] nv = token.trim().split("=", 2);
                    if (nv.length == 2 && "timeout".equalsIgnoreCase(nv[0].trim())) {
                        try {
                            return TimeValue.ofSeconds(Long.parseLong(nv[1].trim()));
                        } catch (NumberFormatException ignore) {
                            // 非法值忽略，回落到默认
                        }
                    }
                }
            }
            return TimeValue.ofSeconds(DEFAULT_KEEP_ALIVE_SECONDS);
        };

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(RETRY_MAX_ATTEMPTS, TimeValue.ofSeconds(RETRY_INTERVAL_SECONDS)))
                .setKeepAliveStrategy(keepAliveStrategy)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(EVICT_IDLE_SECONDS))
                .build();
    }

    /**
     * RestClient：以调优后的 HttpClient 作为底层 requestFactory，统一默认 Content-Type 与请求拦截器位。
     * 业务侧可声明同名 Bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient restClient(CloseableHttpClient closeableHttpClient) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(closeableHttpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 全局请求拦截器：预留 traceId 透传/请求日志位点（生产环境勿打印 body）
                .requestInterceptor((request, body, execution) -> execution.execute(request, body))
                .build();
    }
}
