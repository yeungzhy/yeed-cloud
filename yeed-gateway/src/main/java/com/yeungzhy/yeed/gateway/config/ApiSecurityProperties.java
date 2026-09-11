package com.yeungzhy.yeed.gateway.config;

import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenApi 报文加解密安全参数（配置中心可刷新）
 *
 * <p>配置示例：
 * {@snippet lang="yaml":
 * yeed-gateway:
 *   api-security:
 *     enabled: true
 *     public-prefixes:
 *       - /openapi
 *     allow-disparity: 5m
 *     nonce-ttl: 10m
 *     max-encrypted-body-bytes: 1MB
 * }
 *
 * <p>{@code public-prefixes} 是「公网加密前缀」：只对这些前缀下的请求做完整报文加解密，
 * 其余路径全部直通（比"反向登记排除名单"更不易漏，见 openapi-security-design.md §11.1）
 *
 * <p>平台 RSA 私钥不在此处，统一取 {@link CryptoProperties}（{@code crypto.rsa.private-key}），
 * 避免密钥出现第二份真源
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yeed-gateway.api-security")
public class ApiSecurityProperties {

    /** 总开关：置 false 时全部路径直通（排障 / 灰度期用），默认开启 */
    private boolean enabled = true;

    /** 公网加密前缀：命中任一前缀的请求走完整加解密流程 */
    private List<String> publicPrefixes = new ArrayList<>(List.of("/openapi"));

    /** timestamp 允许偏差（取绝对值比较），默认 1 分钟 */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration allowDisparity = Duration.ofMinutes(1);

    /** nonce 去重键 TTL，默认 2 分钟（= 2 倍时间窗，消除服务器间时钟差异导致的绕过期） */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration nonceTtl = Duration.ofMinutes(2);

    /** 密文请求体上限，默认 1MB，超过即 413（须先缓冲才能验 tag，故必须限流） */
    private DataSize maxEncryptedBodyBytes = DataSize.ofMegabytes(1);

}
