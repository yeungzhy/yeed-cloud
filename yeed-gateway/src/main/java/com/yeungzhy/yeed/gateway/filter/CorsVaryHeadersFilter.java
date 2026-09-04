package com.yeungzhy.yeed.gateway.filter;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * 透传响应头清洗：移除下游响应中可能重复的 CORS Vary 头
 *
 * <p>CORS Vary（{@code Origin / Access-Control-Request-Method /
 * Access-Control-Request-Headers}）在响应中存在两个来源：
 * <ul>
 *   <li>网关 CorsWebFilter（见 {@code WebFilterConfig#corsWebFilter()}）：请求入站时
 *       {@code DefaultCorsProcessor} 无条件添加一组，统一保证所有响应携带 CORS 头；</li>
 *   <li>下游 servlet 服务的 404 兜底响应：Spring MVC 的 {@code ResourceHttpRequestHandler}
 *       实现了 {@code CorsConfigurationSource}，{@code AbstractHandlerMapping} 对它会无条件挂载
 *       {@code CorsInterceptor}（即使服务自身零跨域配置），每请求调用 {@code DefaultCorsProcessor}
 *       又无条件追加一组同值 Vary（该方法使用 {@code addAll} 追加而非覆盖）。
 *       这才是重复 Vary 的根因——并非下游主动配置了 CORS。</li>
 * </ul>
 * 网关透传下游响应头时两组同值 Vary 合并成重复头，本过滤器在透传阶段（{@link Type#RESPONSE}）
 * 仅剔除这三项、由网关统一维护；其它 Vary（如 {@code Accept-Encoding}）原样保留，不做多余处理。
 */
public class CorsVaryHeadersFilter implements HttpHeadersFilter {

    /** 网关 CorsWebFilter 统一维护的 CORS Vary 值，透传时不再保留下游的重复声明 */
    private static final List<String> CORS_VARY_HEADERS = List.of(
            HttpHeaders.ORIGIN,
            HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS
    );

    @Override
    public boolean supports(Type type) {
        return type == Type.RESPONSE;
    }

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        // 分支预测: 仅下游服务响应头里有 Vary 头时才做处理
        if (!input.containsKey(HttpHeaders.VARY)) {
            return input;
        }
        // 提取 Vary 值并剔除 CORS 三项，保留其它 Vary（如 Accept-Encoding）
        List<String> varyValues = input.get(HttpHeaders.VARY);
        List<String> retained = varyValues.stream()
                .map(String::trim)
                .filter(v -> !CORS_VARY_HEADERS.contains(v))
                .toList();
        // 一个都没剔除说明下游本就没声明 CORS Vary，无重复，原样返回
        if (retained.size() == varyValues.size()) {
            return input;
        }
        // 重建头对象：除 Vary 键外全部原样拷贝，避免原对象里的重复 Vary 残留
        HttpHeaders filtered = new HttpHeaders();
        input.forEach((key, values) -> {
            if (!key.equalsIgnoreCase(HttpHeaders.VARY)) {
                filtered.addAll(key, values);
            }
        });
        // 有保留的非 CORS Vary 时写回重建结果，保证其它 Vary 语义不丢
        if (!retained.isEmpty()) {
            filtered.addAll(HttpHeaders.VARY, retained);
        }
        return filtered;
    }

}
