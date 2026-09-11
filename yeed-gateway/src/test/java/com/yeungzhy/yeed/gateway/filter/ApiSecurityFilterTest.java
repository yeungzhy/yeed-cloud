package com.yeungzhy.yeed.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.common.core.constant.CacheConstant;
import com.yeungzhy.yeed.common.core.crypto.AesUtil;
import com.yeungzhy.yeed.common.core.crypto.CryptoProperties;
import com.yeungzhy.yeed.common.core.crypto.RsaUtil;
import com.yeungzhy.yeed.gateway.config.ApiSecurityExcludeProperties;
import com.yeungzhy.yeed.gateway.config.ApiSecurityProperties;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiAppKeyResolver;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiCryptoCodec;
import com.yeungzhy.yeed.gateway.filter.openapi.OpenapiReplayGuard;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * ApiSecurityFilter 单测：不起容器、不连 Redis，用真实的 RsaUtil / AesUtil 扮演客户端
 *
 * <p> 客户端要做的只有三步：
 * <ol>
 * <li>随机一个 32 字节会话 key，用平台公钥加密后放进 X-Enc-Key</li>
 * <li>用它以 AES-256-GCM 加密 body，AAD 取 {@code appId|method|path|timestamp|nonce}</li>
 * <li>补齐版本、算法、时间戳、nonce 四个头（B2B 再加 appId 与签名）</li>
 * </ol>
 *
 * <p> 覆盖：C 端正常链路、AAD 篡改、nonce 重放、必填头缺失、时间戳过期、B2B 验签（含上一把公钥）、
 * 公网前缀之外的直通
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public class ApiSecurityFilterTest {

    /** 命中默认公网前缀 /openapi 的测试路径 */
    private static final String PATH = "/openapi/probe";

    private static final String VERSION = "1";
    private static final String ENC_ALG = "RSA-AES256-GCM";
    private static final String PLAIN_BODY = "{\"name\":\"yeed\"}";
    private static final String RESP_BODY = "{\"code\":0,\"msg\":\"success\",\"data\":\"ok\"}";
    private static final byte[] EMPTY_BODY = new byte[0];

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 平台密钥对（私钥通过 CryptoProperties 注入过滤器，公钥用来扮演客户端） */
    private String platformPublicKey;
    private String platformPrivateKey;

    /** 假 Redis 的数据面：键即缓存键，值即缓存值 */
    private Map<String, Object> redisData;

    private ApiSecurityFilter filter;

    @Before
    public void setUp() throws Exception {
        String[] platformPair = RsaUtil.generateKeyPair();
        platformPublicKey = platformPair[0];
        platformPrivateKey = platformPair[1];
        redisData = new ConcurrentHashMap<>();
        ApiSecurityProperties securityProperties = new ApiSecurityProperties();
        RedissonClient redis = fakeRedis();
        filter = new ApiSecurityFilter(Ordered.HIGHEST_PRECEDENCE + 100, securityProperties, new ApiSecurityExcludeProperties(),
                new OpenapiReplayGuard(redis, securityProperties),
                new OpenapiAppKeyResolver(redis, JSON),
                new OpenapiCryptoCodec(cryptoProperties(platformPrivateKey), securityProperties),
                JSON);
    }

    /** 场景 1：C 端正常请求，明文下传 + 响应密文回写 */
    @Test
    public void shouldForwardPlainTextAndEncryptResponse() {
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-ok";
        byte[] cipher = encryptBody(sessionKey, "", PATH, timestamp, nonce, PLAIN_BODY);

        MockServerWebExchange exchange = encryptedExchange(PATH, timestamp, nonce, sessionKey, cipher, null, null);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        // 下游收到明文，加密头已剥除，Content-Length 改写为明文长度
        assertArrayEquals(PLAIN_BODY.getBytes(StandardCharsets.UTF_8), chain.body);
        assertNull(chain.headers.getFirst("X-Enc-Key"));
        assertNull(chain.headers.getFirst("X-Nonce"));
        assertEquals(PLAIN_BODY.getBytes(StandardCharsets.UTF_8).length, chain.headers.getContentLength());

        // 客户端收到密文，AAD 回显请求的 timestamp|nonce
        assertEquals(VERSION, exchange.getResponse().getHeaders().getFirst("X-Enc-Version"));
        assertEquals(RESP_BODY, decryptResponse(exchange, sessionKey, timestamp, nonce));
    }

    /** 场景 2：AAD 被篡改（密文是给 /openapi/other 的，却发到 PATH），GCM 的 tag 校验必然失败 */
    @Test
    public void shouldRejectWhenAadTampered() {
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-aad";
        byte[] cipher = encryptBody(sessionKey, "", "/openapi/other", timestamp, nonce, PLAIN_BODY);

        MockServerWebExchange exchange = encryptedExchange(PATH, timestamp, nonce, sessionKey, cipher, null, null);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertRejected(exchange, 400, 400102);
        assertNull("解密失败不应进入下游", chain.body);
    }

    /** 场景 3：nonce 已被占用（重放），Redis SET NX 返回 false */
    @Test
    public void shouldRejectReplayedNonce() {
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-replay";
        byte[] cipher = encryptBody(sessionKey, "", PATH, timestamp, nonce, PLAIN_BODY);
        // C 端 appId 为空串，占位键形如 yeed:openapi:sign:nonce::nonce-replay
        redisData.put(CacheConstant.OPENAPI_SIGN_NONCE_PREFIX + ":" + nonce, "1");

        MockServerWebExchange exchange = encryptedExchange(PATH, timestamp, nonce, sessionKey, cipher, null, null);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertRejected(exchange, 403, 403100);
        assertNull(chain.body);
    }

    /** 场景 4：缺 X-Enc-Key，必填头校验即拦下 */
    @Test
    public void shouldRejectWhenEncKeyMissing() {
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        byte[] cipher = encryptBody(sessionKey, "", PATH, timestamp, "nonce-no-key", PLAIN_BODY);

        MockServerWebExchange exchange =
                encryptedExchange(PATH, timestamp, "nonce-no-key", null, cipher, null, null);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertRejected(exchange, 400, 400100);
        assertNull(chain.body);
    }

    /** 场景 5：timestamp 超出允许偏差（默认 1 分钟） */
    @Test
    public void shouldRejectWhenTimestampExpired() {
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis() - 5 * 60 * 1000L;
        byte[] cipher = encryptBody(sessionKey, "", PATH, timestamp, "nonce-old", PLAIN_BODY);

        MockServerWebExchange exchange = encryptedExchange(PATH, timestamp, "nonce-old", sessionKey, cipher, null, null);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertRejected(exchange, 401, 401100);
        assertNull(chain.body);
    }

    /** 场景 6：B2B 验签通过（公钥缓存以 Map 形态存放，覆盖 Map 分支） */
    @Test
    public void shouldAcceptB2BRequestWithValidSign() throws Exception {
        String appId = "app-demo";
        String[] appPair = RsaUtil.generateKeyPair();
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-b2b";
        // B2B 的 AAD 首段是 appId
        byte[] cipher = encryptBody(sessionKey, appId, PATH, timestamp, nonce, PLAIN_BODY);
        String sign = RsaUtil.sign(appPair[1], signBase(appId, PATH, timestamp, nonce, cipher));

        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("publicKey", appPair[0]);
        cached.put("publicKeyPrev", null);
        redisData.put(CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + appId, cached);

        MockServerWebExchange exchange =
                encryptedExchange(PATH, timestamp, nonce, sessionKey, cipher, appId, sign);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertArrayEquals(PLAIN_BODY.getBytes(StandardCharsets.UTF_8), chain.body);
    }

    /** 场景 7：轮换过渡期内用上一把私钥签名仍放行（公钥缓存以 JSON 字符串存放，覆盖 String 分支） */
    @Test
    public void shouldAcceptRequestSignedByPreviousKey() throws Exception {
        String appId = "app-rotate";
        String[] oldPair = RsaUtil.generateKeyPair();
        String[] newPair = RsaUtil.generateKeyPair();
        byte[] sessionKey = newSessionKey();
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-rotate";
        byte[] cipher = encryptBody(sessionKey, appId, PATH, timestamp, nonce, PLAIN_BODY);
        String sign = RsaUtil.sign(oldPair[1], signBase(appId, PATH, timestamp, nonce, cipher));

        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("publicKey", newPair[0]);
        cached.put("publicKeyPrev", oldPair[0]);
        // 上一把尚未过期（epoch 毫秒，写入方是 yeed-openapi）
        cached.put("prevKeyExpireTime", System.currentTimeMillis() + 10 * 60 * 1000L);
        redisData.put(CacheConstant.OPENAPI_APP_PUBKEY_PREFIX + appId, JSON.writeValueAsString(cached));

        MockServerWebExchange exchange =
                encryptedExchange(PATH, timestamp, nonce, sessionKey, cipher, appId, sign);
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertArrayEquals(PLAIN_BODY.getBytes(StandardCharsets.UTF_8), chain.body);
    }

    /** 场景 8：公网前缀之外的路径整条直通，不做任何加解密 */
    @Test
    public void shouldPassThroughOutsidePublicPrefix() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/admin/sys/user").body(PLAIN_BODY));
        CapturingChain chain = new CapturingChain();
        filter.filter(exchange, chain).block();

        assertArrayEquals(PLAIN_BODY.getBytes(StandardCharsets.UTF_8), chain.body);
        assertNull(exchange.getResponse().getHeaders().getFirst("X-Enc-Version"));
    }

    // ==================== 扮演客户端 ====================

    /** 组装一个加密请求（sessionKey 传 null 即不带 X-Enc-Key，appId 传 null 即 C 端） */
    private MockServerWebExchange encryptedExchange(String path, long timestamp, String nonce, byte[] sessionKey,
                                                    byte[] cipher, String appId, String sign) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post(path)
                .header("X-Enc-Version", VERSION)
                .header("X-Enc-Alg", ENC_ALG)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce);
        if (appId != null) {
            builder = builder.header("X-App-Id", appId).header("X-Sign", sign);
        }
        if (sessionKey != null) {
            String encryptedKey = RsaUtil.encrypt(platformPublicKey, Base64.getEncoder().encodeToString(sessionKey));
            builder = builder.header("X-Enc-Key", encryptedKey);
        }
        return MockServerWebExchange.from(
                builder.contentLength(cipher.length).body(Flux.just(new DefaultDataBufferFactory().wrap(cipher))));
    }

    /** 按协议加密 body：AAD 绑定 appId / 方法 / 路径 / 时间戳 / nonce */
    private static byte[] encryptBody(byte[] sessionKey, String appId, String path, long timestamp,
                                      String nonce, String plainText) {
        String aad = appId + "|POST|" + path + "|" + timestamp + "|" + nonce;
        return AesUtil.encrypt(
                sessionKey,
                plainText.getBytes(StandardCharsets.UTF_8),
                aad.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** B2B 待签基串：method\npath\nappId\ntimestamp\nnonce\nBase64(SHA-256(cipher)) */
    private static String signBase(String appId, String path, long timestamp, String nonce, byte[] cipher)
            throws Exception {
        String digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(cipher));
        return "POST\n" + path + "\n" + appId + "\n" + timestamp + "\n" + nonce + "\n" + digest;
    }

    /** 解密平台响应：响应 AAD 只回显请求的 timestamp|nonce */
    private static String decryptResponse(MockServerWebExchange exchange, byte[] sessionKey,
                                          long timestamp, String nonce) {
        byte[] cipher = responseBytes(exchange);
        String aad = timestamp + "|" + nonce;
        return new String(AesUtil.decrypt(sessionKey, cipher, aad.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    private static byte[] newSessionKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    // ==================== 断言辅助 ====================

    private static void assertRejected(MockServerWebExchange exchange, int status, int code) {
        assertEquals(status, exchange.getResponse().getStatusCode().value());
        Map<?, ?> body = readJson(exchange);
        Object actual = body.get("code");
        assertNotNull("错误响应缺少 code", actual);
        assertEquals(code, ((Number) actual).intValue());
        assertNotNull(body.get("msg"));
    }

    private static Map<?, ?> readJson(MockServerWebExchange exchange) {
        try {
            return JSON.readValue(responseBytes(exchange), Map.class);
        } catch (Exception e) {
            throw new AssertionError("响应不是合法 JSON", e);
        }
    }

    private static byte[] responseBytes(MockServerWebExchange exchange) {
        MockServerHttpResponse response = exchange.getResponse();
        DataBuffer joined = DataBufferUtils.join(response.getBody()).block();
        return joined == null ? EMPTY_BODY : readBytes(joined);
    }

    private static byte[] readBytes(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return bytes;
    }

    // ==================== 假 Redis ====================

    /**
     * CryptoProperties 的 setter 是包私有（只允许框架绑定），单测用反射注入平台私钥
     *
     * @param privateKey 平台 RSA 私钥
     * @return 已注入的配置对象
     */
    private static CryptoProperties cryptoProperties(String privateKey) throws Exception {
        CryptoProperties properties = new CryptoProperties();
        Field field = CryptoProperties.class.getDeclaredField("rsa");
        field.setAccessible(true);
        field.set(properties, new CryptoProperties.RsaKeyPair().setPrivateKey(privateKey));
        return properties;
    }

    /** 假 Redisson：动态代理只顶替过滤器真正用到的三个方法，省去为单测起 Redis */
    private RedissonClient fakeRedis() {
        return (RedissonClient) Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class}, (proxy, method, args) -> {
                    if ("getBucket".equals(method.getName())) {
                        return fakeBucket((String) args[0]);
                    }
                    return fallback(method, args, proxy);
                });
    }

    private RBucket<Object> fakeBucket(String key) {
        return (RBucket<Object>) Proxy.newProxyInstance(RBucket.class.getClassLoader(),
                new Class<?>[]{RBucket.class}, (proxy, method, args) -> switch (method.getName()) {
                    // 真实 Redis 的 SET NX 语义：键不存在才写入并返回 true
                    case "setIfAbsentAsync" -> completed(redisData.putIfAbsent(key, "1") == null);
                    case "getAsync" -> completed(redisData.get(key));
                    default -> fallback(method, args, proxy);
                });
    }

    /** 一个立即完成（同步回调）的假 RFuture */
    @SuppressWarnings("unchecked")
    private static <T> RFuture<T> completed(T value) {
        return (RFuture<T>) Proxy.newProxyInstance(RFuture.class.getClassLoader(),
                new Class<?>[]{RFuture.class}, (proxy, method, args) -> {
                    if ("whenComplete".equals(method.getName())) {
                        ((BiConsumer<T, Throwable>) args[0]).accept(value, null);
                        return proxy;
                    }
                    return fallback(method, args, proxy);
                });
    }

    private static Object fallback(Method method, Object[] args, Object proxy) {
        return switch (method.getName()) {
            case "toString" -> "fake-redis";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return (char) 0;
    }

    /** 假过滤器链：记下下游收到的请求，并回写一段固定明文响应 */
    private static final class CapturingChain implements GatewayFilterChain {

        private HttpHeaders headers;
        private byte[] body;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.headers = exchange.getRequest().getHeaders();
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .map(ApiSecurityFilterTest::readBytes)
                    .defaultIfEmpty(EMPTY_BODY)
                    .doOnNext(bytes -> this.body = bytes)
                    .then(exchange.getResponse().writeWith(Mono.just(
                            exchange.getResponse().bufferFactory().wrap(RESP_BODY.getBytes(StandardCharsets.UTF_8)))));
        }
    }

}
