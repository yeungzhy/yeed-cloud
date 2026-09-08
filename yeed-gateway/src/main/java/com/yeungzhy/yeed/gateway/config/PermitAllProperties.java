package com.yeungzhy.yeed.gateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关放行接口配置（无需认证即可访问）
 *
 * <p>配置示例：
 * {@snippet lang="yaml":
 * yeed-gateway:
 *   auth:
 *     permit-all:
 *       paths:
 *         - /auth/login
 * }
 *
 * <p>命中名单的请求在网关跳过登录校验与权限校验，直接放行，
 * 仅用于无需任何认证的匿名接口；名单支持 Ant 风格通配
 *
 * @author yeungzhy
 * @since 2026-08-15
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yeed-gateway.auth.permit-all")
public class PermitAllProperties {

    /**
     * 无需认证即可访问的接口路径（Ant 风格通配），如 /auth/login
     *
     * <p>元素 {@link NotBlank} 挡空项：名单由鉴权过滤器逐条匹配，混入 null / 空串会在请求期 NPE；
     * 列表整体为空是合法的（等于不放行任何匿名接口），故不加 {@code @NotEmpty}
     */
    private List<@NotBlank String> paths = new ArrayList<>();

}
