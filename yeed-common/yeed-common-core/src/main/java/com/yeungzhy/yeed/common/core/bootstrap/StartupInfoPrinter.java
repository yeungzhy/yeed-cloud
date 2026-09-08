package com.yeungzhy.yeed.common.core.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 应用启动信息打印器
 *
 * <p> 监听 {@link ApplicationReadyEvent}，等容器就绪且所有 ApplicationRunner 成功执行后才输出，
 * 避免「先打印启动成功、随后启动失败」的误导
 *
 * <p> 使用方式
 * <ol>
 *   <li>由 {@link com.yeungzhy.yeed.common.core.config.StartupInfoPrinterAutoConfiguration} 注册，
 *       引入 yeed-common-core 即生效，无需额外配置
 *   <li>自定义 Banner：在服务模块 {@code src/main/resources/} 下放置 {@code banner.txt}，
 *       ClassPath 优先级保证当前服务覆盖公共默认 Banner；未提供则跳过
 * </ol>
 *
 * <p> 运行环境兼容
 * <ul>
 *   <li>Servlet Web（admin / auth 等）：通过 {@link WebServerApplicationContext} 取实际绑定端口
 *   <li>Reactive Web（gateway 等）：反射获取 {@code ReactiveWebServerApplicationContext} 的端口
 *   <li>非 Web 服务（job 等）：读 {@code server.port} 配置或显示 {@code N/A}
 * </ul>
 *
 * @author yeungzhy
 * @since 2026-08-09
 */
@Slf4j
public class StartupInfoPrinter {

    /** Reactive Web 上下文全限定名（避免对 WebFlux 的硬依赖） */
    private static final String REACTIVE_WEB_SERVER_CONTEXT =
            "org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext";
    /** spring.application.name 配置键 */
    private static final String KEY_APPLICATION_NAME = "spring.application.name";

    @EventListener(ApplicationReadyEvent.class)
    public void printStartupInfo(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();

        String banner = loadBanner();
        if (!banner.isEmpty()) {
            log.info(AnsiOutput.toString(AnsiColor.GREEN, banner));
        }
        log.info(AnsiOutput.toString(
                AnsiColor.BRIGHT_YELLOW, resolveApplicationName(env),
                AnsiColor.BRIGHT_GREEN, " Spring Boot ", SpringBootVersion.getVersion()));
        log.info(AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, "Listening on port: ", resolvePort(event)));
        log.info(AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, "Active Profile: ", resolveActiveProfiles(env)));
        log.info(AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, "Started in: ", formatTimeTaken(event), " s"));
    }

    /**
     * 解析应用名；未配置 spring.application.name 时返回 unknown
     */
    private String resolveApplicationName(Environment env) {
        return env.getProperty(KEY_APPLICATION_NAME, "unknown");
    }

    /**
     * 从 classpath:banner.txt 加载 Banner 内容。
     * 优先加载当前服务模块的资源，找不到时返回空串（跳过 Banner 打印）。
     * <p>使用纯 JDK {@code InputStream.readAllBytes()}（Java 11+），避免引入 commons-io 依赖。
     */
    private String loadBanner() {
        ClassPathResource resource = new ClassPathResource("banner.txt");
        if (!resource.exists()) {
            return "";
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load banner.txt, skip banner printing.", e);
            return "";
        }
    }

    /**
     * 解析实际监听端口，兼容三种运行环境
     * <ol>
     *   <li>Servlet Web → 从 WebServerApplicationContext 取实际绑定端口（兼容 server.port=0 随机端口）
     *   <li>Reactive Web → 反射调用 ReactiveWebServerApplicationContext 取端口
     *   <li>非 Web 环境 → 读 server.port 配置，缺失返回 N/A
     * </ol>
     */
    private String resolvePort(ApplicationReadyEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        // 1) Servlet Web（Spring MVC）
        if (ctx instanceof WebServerApplicationContext webServerContext) {
            return String.valueOf(webServerContext.getWebServer().getPort());
        }

        // 2) Reactive Web（Spring WebFlux / Gateway）：反射判断，避免对 WebFlux 硬依赖
        try {
            Class<?> reactiveCtxClass = Class.forName(REACTIVE_WEB_SERVER_CONTEXT);
            if (reactiveCtxClass.isInstance(ctx)) {
                Object webServer = reactiveCtxClass.getMethod("getWebServer").invoke(ctx);
                Object port = webServer.getClass().getMethod("getPort").invoke(webServer);
                return String.valueOf(port);
            }
        } catch (ClassNotFoundException ignored) {
            // WebFlux 不在 classpath，跳过
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to resolve reactive web server port via reflection, fallback to config.", e);
        }

        // 3) 非 Web 环境兜底
        return event.getApplicationContext().getEnvironment().getProperty("server.port", "N/A");
    }

    /**
     * 拼接激活的 Profile；未显式激活时显示 default
     */
    private String resolveActiveProfiles(Environment env) {
        String[] profiles = env.getActiveProfiles();
        return profiles.length > 0 ? String.join(",", profiles) : "default";
    }

    /**
     * 启动耗时（秒，保留两位小数）
     */
    private String formatTimeTaken(ApplicationReadyEvent event) {
        return String.format("%.2f", event.getTimeTaken().toMillis() / 1000.0);
    }

}
