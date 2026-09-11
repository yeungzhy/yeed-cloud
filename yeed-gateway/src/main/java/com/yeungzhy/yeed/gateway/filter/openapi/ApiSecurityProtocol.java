package com.yeungzhy.yeed.gateway.filter.openapi;

import java.util.Set;

/**
 * OpenApi 加解密协议常量（头名、版本、算法、明文错误码）
 *
 * <p>网关侧解析、剥除、回写与客户端契约共用同一组常量，防止拼写漂移
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public final class ApiSecurityProtocol {
    private ApiSecurityProtocol() {
        throw new UnsupportedOperationException("ApiSecurityProtocol is a utility class and cannot be instantiated");
    }

    /** 加密协议版本 */
    public static final String VERSION = "1";

    /** 加密算法标识 */
    public static final String ENC_ALG = "RSA-AES256-GCM";

    /** 协议版本头 */
    public static final String HDR_ENC_VERSION = "X-Enc-Version";

    /** 加密算法头 */
    public static final String HDR_ENC_ALG = "X-Enc-Alg";

    /** 会话密钥头（平台公钥加密） */
    public static final String HDR_ENC_KEY = "X-Enc-Key";

    /** 请求时间戳头（毫秒） */
    public static final String HDR_TIMESTAMP = "X-Timestamp";

    /** 防重放随机串头 */
    public static final String HDR_NONCE = "X-Nonce";

    /** 接入应用标识头（C 端不带） */
    public static final String HDR_APP_ID = "X-App-Id";

    /** B2B 签名头 */
    public static final String HDR_SIGN = "X-Sign";

    /** 需要从转发明文请求中剥除的加密头 */
    public static final Set<String> ENCRYPT_HEADERS = Set.of(
            HDR_ENC_VERSION, HDR_ENC_ALG, HDR_ENC_KEY, HDR_TIMESTAMP, HDR_NONCE, HDR_APP_ID, HDR_SIGN);

    /** 必填头缺失或格式非法 */
    public static final int CODE_HEADER_MISSING = 400100;

    /** 加密请求未携带 Content-Length（不接受 chunked） */
    public static final int CODE_CONTENT_LENGTH_MISSING = 400101;

    /** 密文解密失败（不区分具体原因，防探测） */
    public static final int CODE_CRYPTO_BAD = 400102;

    /** 密文请求体超过上限 */
    public static final int CODE_BODY_TOO_LARGE = 400103;

    /** 时间戳偏差过大或已过期 */
    public static final int CODE_TIMESTAMP_SKEW = 401100;

    /** 验签失败或 appId 不可用 */
    public static final int CODE_SIGN_INVALID = 401101;

    /** nonce 重复或防重放服务不可用 */
    public static final int CODE_NONCE_REPLAY = 403100;


}
