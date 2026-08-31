package com.yeungzhy.yeed.gateway.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问日志排除配置（不记录访问日志的接口名单）
 *
 * <p>配置示例（修改即时生效，无需重启网关）：
 * {@snippet lang="yaml":
 * yeed-gateway:
 *   log-exclude:
 *     headers:
 *       - X-Test
 *     paths:
 *       - /yeed/**
 * }
 *
 * <p>{@code headers} 命中任一即不记录（性能测试、压测等标识，大小写不敏感）
 *
 * <p>{@code paths} 支持精确路径与 Ant 风格通配符（如 {@code /yeed/**}）
 *
 * @author yeungzhy
 * @since 2026-08-30
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yeed-gateway.access-log.exclude")
public class AccessLogExcludeProperties {

    /** 命中任一即不记录访问日志的请求头（性能测试、压测等标识） */
    @NotEmpty
    private List<String> headers = new ArrayList<>(List.of(
            // 通用测试 / 冒烟 / 自动化测试标识
            "X-Test",
            "X-Smoke-Test",
            "X-Auto-Test",
            // 压测 / 性能测试标识
            "X-Load-Test",
            "X-Pressure-Test",
            "X-Perf-Test"
    ));

    /** 不记录访问日志的接口路径（精确路径与 Ant 风格通配符） */
    @NotEmpty
    private List<String> paths = new ArrayList<>(List.of("/yeed/**"));

    /** paths 的预编译结果：{@link #setPaths} 时同步编译并校验 */
    private volatile List<PathPattern> pathPatterns = compile(paths);

    /** 路径模式编译器：线程安全，全局共享 */
    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();


    public void setPaths(List<String> paths) {
        this.paths = paths;
        this.pathPatterns = compile(paths);
    }

    /**
     * 编译路径模式，启动绑定与
     * Nacos 刷新（rebind 调 {@link #setPaths}）都会走到这里
     */
    private static List<PathPattern> compile(List<String> patterns) {
        List<PathPattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(PATH_PATTERN_PARSER.parse(pattern));
        }
        return List.copyOf(compiled);
    }


}
