package com.yeungzhy.yeed.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenApi 加解密豁免路径配置（不走报文加解密、直接放行的公网端点）
 *
 * <p>配置示例（修改即时生效，无需重启网关）：
 * {@snippet lang="yaml":
 * yeed-gateway:
 *   api-security:
 *     exclude:
 *       paths:
 *         - /openapi/health/**
 *         - /openapi/file/**
 * }
 *
 * <p>作用域限定在 {@link ApiSecurityProperties#getPublicPrefixes()} 内：不在公网前缀的请求
 * 本就不会走加解密，无需在此登记；此名单只登记"公网前缀下仍须明文直通的端点"
 * （如 §8 文件通道的一次性凭证端点、健康检查等）
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yeed-gateway.api-security.exclude")
public class ApiSecurityExcludeProperties {

    /** 豁免加密的路径（支持精确路径与 Ant 风格通配符，如 {@code /openapi/file/**}） */
    private List<String> paths = new ArrayList<>();

    /** paths 的预编译结果：{@link #setPaths} 时同步编译并校验 */
    private volatile List<PathPattern> pathPatterns = compile(paths);

    /** 路径模式编译器：线程安全，全局共享 */
    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    /**
     * 是否命中豁免名单
     *
     * @param request 当前请求
     * @return true=豁免（明文直通）
     */
    public boolean isExcluded(ServerHttpRequest request) {
        PathContainer pathContainer = request.getPath();
        for (PathPattern pattern : pathPatterns) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
        this.pathPatterns = compile(paths);
    }

    /**
     * 编译路径模式
     *
     * <p>启动绑定与 Nacos 刷新（rebind 会调 {@link #setPaths}）都走这里，非法模式在绑定期即暴露
     *
     * @param patterns 待编译的路径模式，元素不能为 null
     * @return 编译结果，不可变列表
     */
    private static List<PathPattern> compile(List<String> patterns) {
        List<PathPattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(PATH_PATTERN_PARSER.parse(pattern));
        }
        return List.copyOf(compiled);
    }

}
