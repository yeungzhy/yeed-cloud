package com.yeungzhy.yeed.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关放行接口配置（无需认证即可访问）
 *
 * <p>配置示例（Nacos 中 yeed-gateway 配置，按环境隔离）：
 *
 * {@snippet lang="yaml":
 * app:
 *   auth:
 *     permit-all:
 *       paths:
 *         - /auth/login
 *         - /auth/captcha
 * }
 *
 * <p>命中名单的请求在网关跳过登录校验与权限校验，直接放行，仅用于无需任何认证的匿名接口；
 * 名单支持 Ant 风格通配（如 {@code /auth/**}）。业务接口的权限控制仍走「接口路径 → 权限码」缓存鉴权，
 * 不要将业务接口塞入此名单。
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Data
@ConfigurationProperties(prefix = "app.auth.permit-all")
public class PermitAllProperties {

    /** 无需认证即可访问的接口路径（Ant 风格通配），如 /auth/login */
    private List<String> paths = new ArrayList<>();

}
