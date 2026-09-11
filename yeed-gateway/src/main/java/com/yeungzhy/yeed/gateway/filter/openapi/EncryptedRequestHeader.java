package com.yeungzhy.yeed.gateway.filter.openapi;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 已解析的加密头（必填头、版本、算法、nonce 格式、时间戳已校验通过）
 *
 * @param timestamp 请求时间戳（毫秒）
 * @param nonce 防重放随机串
 * @param appId 应用标识（C 端为空串）
 * @param method HTTP 方法
 * @param path 请求路径
 * @param encKey 平台公钥加密的会话 AES key
 * @param sign B2B 签名（C 端为空串）
 * @param b2b 是否 B2B 请求
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public record EncryptedRequestHeader(long timestamp, String nonce, String appId, String method, String path,
                                     String encKey, String sign, boolean b2b) {

    /**
     * 解析并校验必填加密头
     *
     * @param request 当前请求
     * @return 解析结果
     */
    public static EncryptedRequestHeader parse(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        String version = requireHeader(headers, ApiSecurityProtocol.HDR_ENC_VERSION);
        if (!ApiSecurityProtocol.VERSION.equals(version)) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "不支持的 X-Enc-Version：" + version);
        }
        String alg = requireHeader(headers, ApiSecurityProtocol.HDR_ENC_ALG);
        if (!ApiSecurityProtocol.ENC_ALG.equals(alg)) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "不支持的 X-Enc-Alg：" + alg);
        }
        String encKey = requireHeader(headers, ApiSecurityProtocol.HDR_ENC_KEY);
        String timestampText = requireHeader(headers, ApiSecurityProtocol.HDR_TIMESTAMP);
        String nonce = requireHeader(headers, ApiSecurityProtocol.HDR_NONCE);
        // nonce 会拼进换行分隔的签名基串，禁 CR/LF 以防字段边界被伪造
        if (nonce.length() > 128 || nonce.indexOf('\n') >= 0 || nonce.indexOf('\r') >= 0) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "X-Nonce 格式非法");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "X-Timestamp 必须是毫秒时间戳");
        }

        String appId = headers.getFirst(ApiSecurityProtocol.HDR_APP_ID);
        boolean b2b = appId != null;
        if (b2b && !StringUtils.hasText(appId)) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "X-App-Id 不能为空");
        }
        String appIdValue = b2b ? appId.trim() : "";
        String sign = headers.getFirst(ApiSecurityProtocol.HDR_SIGN);
        if (b2b && !StringUtils.hasText(sign)) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "B2B 请求必填头缺失：X-Sign");
        }
        String signValue = sign == null ? "" : sign;

        HttpMethod httpMethod = request.getMethod();
        if (httpMethod == null) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "缺少 HTTP 方法");
        }
        return new EncryptedRequestHeader(timestamp, nonce, appIdValue, httpMethod.name(),
                request.getPath().value(), encKey, signValue, b2b);
    }

    /**
     * 请求 AAD：{@code appId|method|path|timestamp|nonce}（C 端 appId 为空段）
     *
     * <p>用竖线分隔：AAD 只在两端各自的加解密代码中计算，属内部约定，取日志里易读的分隔符，
     * 对外公开的签名基串另用换行分隔，两者的理由各不相同
     *
     * @return AAD 字节
     */
    public byte[] requestAad() {
        String aadText = """
                %s|%s|%s|%s|%s""".formatted(appId, method, path, timestamp, nonce);
        return aadText.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 响应 AAD：只回显请求的 {@code timestamp|nonce}
     *
     * @return AAD 字节
     */
    public byte[] responseAad() {
        return (timestamp + "|" + nonce).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 取必填头
     *
     * @param headers 请求头
     * @param name 头名
     * @return 值
     */
    private static String requireHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (!StringUtils.hasText(value)) {
            throw new ApiSecurityRejectException(HttpStatus.BAD_REQUEST, ApiSecurityProtocol.CODE_HEADER_MISSING,
                    "必填头缺失：" + name);
        }
        return value;
    }
}
