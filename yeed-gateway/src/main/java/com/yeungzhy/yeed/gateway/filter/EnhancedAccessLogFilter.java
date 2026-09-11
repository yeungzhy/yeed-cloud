package com.yeungzhy.yeed.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yeungzhy.yeed.common.core.enums.FileTypeEnum;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.security.LoginUserInfo;
import com.yeungzhy.yeed.common.core.sensitive.SensitiveJsonUtil;
import com.yeungzhy.yeed.common.core.sensitive.SensitiveTextUtil;
import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import com.yeungzhy.yeed.gateway.config.AccessLogExcludeProperties;
import com.yeungzhy.yeed.gateway.security.MenuCache;
import com.yeungzhy.yeed.gateway.security.MenuCacheSnapshot;
import io.micrometer.tracing.Tracer;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * 增强型网关访问日志过滤器
 *
 * <p>在记录请求路径、方法、状态码等基础访问信息之外，额外提供：
 * <ul>
 *   <li>请求体/响应体采样缓存（防 OOM，超限仅存摘要）
 *   <li>敏感信息自动脱敏（密码、手机号、身份证）
 *   <li>接口中文操作名映射（通过 MenuCache）
 *   <li>全链路 traceId 关联（通过 {@link Tracer} 取当前 span）
 * </ul>
 *
 * <p>日志记录时机：挂在 {@code doFinally} 上，对正常完成、异常终止、客户端取消三种信号都会记录
 *
 * <p>已知限制：网关层异常的 {@code httpStatus} 可能为 null。{@code GlobalWebExceptionHandler}
 * 是 {@code WebExceptionHandler}，其 {@code onErrorResume} 包裹在本过滤器外层，
 * 异常信号先流经本过滤器的 {@code doFinally}、再到达它写状态码，故记录时响应尚未带上状态码，此时以异常摘要为判据
 *
 * @author yeungzhy
 * @since 2026-08-29
 */
@Slf4j
public class EnhancedAccessLogFilter implements GlobalFilter, Ordered {

    /** 请求体 / 响应体缓存上限（字节）：超出部分只记录总字节数，避免 OOM */
    private static final int MAX_CACHED_BODY_BYTES = 32 * 1024;
    /**
     * 请求体采样字节数
     *
     * <p>取上限 +1：采样满 N+1 字节说明原始报文超过 N，报文恰好等于 N 字节时不会被误判为超限
     */
    private static final int BODY_SAMPLE_BYTES = MAX_CACHED_BODY_BYTES + 1;
    /** 空请求体时的采样结果，避免 null 判断扩散 */
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * 请求体超限摘要：占位符取 Content-Length（long），分块传输时为 -1
     */
    private static final String REQUEST_BODY_OVER_LIMIT = """
            {"msg":"request body exceeds limit, %d bytes"}""";
    private static final String REQUEST_BODY_MULTIPART_NOT_CACHED = """
            {"msg":"multipart/form-data not cached, %d bytes"}""";
    private static final String REQUEST_BODY_NOT_FOUND = """
            {"msg":"request body not found"}""";

    /** 响应体占位摘要 */
    private static final String RESPONSE_BODY_OK_FILE_DOWNLOAD = """
            {"code":%d,"msg":"file download, response stream not cached","data":null}"""
            .formatted(ApiResult.CommonCode.OK.getCode());
    private static final String RESPONSE_BODY_NOT_FOUND = """
            {"code":%d,"msg":"response body not found by gateway","data":null}"""
            .formatted(ApiResult.CommonCode.SYSTEM_ERROR.getCode());

    /** 响应体超过上限时保留的根层字段：业务状态码 + 描述 */
    private static final Set<String> RESPONSE_SUMMARY_FIELDS = Set.of(ApiResult.Fields.code, ApiResult.Fields.msg);
    /** 响应体超限、且原文没有 msg 字段时的兜底描述 */
    private static final String RESPONSE_OVER_LIMIT_MSG = "response body exceeds limit, only status code and size retained";
    /** 响应体超限时的体积说明（填入 data 字段） */
    private static final String RESPONSE_OVER_LIMIT_DATA = "response body exceeds limit, %d bytes";

    /**
     * 非 JSON 报文的统一归档字段名：值恒为字符串，入库与返回前端时都是合法 JSON
     *
     * <p>两类内容都以 {@code _raw} 单字段承接，前端渲染不分支：
     * <ul>
     *   <li>文本 / HTML：截断原文（HTML 错误页、纯文本报错往往是排障线索）
     *   <li>二进制（图片、音视频、压缩包、PDF 等）：解码成文本是乱码垃圾，改存一行摘要字符串
     * </ul>
     */
    private static final String NON_JSON_FIELD_RAW = "_raw";
    /** 二进制摘要字符串的组成标记 */
    private static final String BINARY_SUMMARY_MARK = "[binary] type=%s, %d bytes";

    /** 报文的反序列化目标类型 */
    private static final TypeReference<Object> STRUCTURED_BODY_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> BODY_MAP_TYPE = new TypeReference<>() {};

    /** exchange 属性键（过滤器间传递缓存报文与异常的契约键） */
    public static final String CACHED_REQUEST_BODY_JSON_KEY = "cachedJsonKey";
    public static final String CACHED_REQUEST_BODY_X_WWW_FORM_URLENCODED_KEY = "cachedRequestBodyXWwwFormUrlEncodedKey";
    public static final String CACHED_RESPONSE_BODY_KEY = "cachedResponseBodyKey";
    public static final String CACHED_ERROR_MESSAGE_KEY = "cachedErrorMessageKey";

    /** X-Forwarded-For 请求头名（HttpHeaders 无对应常量） */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 文件类响应 MIME → 文件扩展名
     *
     * <p>两个用途共用这一份清单，不另建集合：
     * <ul>
     *   <li>识别文件下载（{@link #isFileDownload}）：该类响应不缓存报文，只记下载摘要
     *   <li>识别"原文无归档价值"的响应（{@link #isBinaryBody}）：{@code _raw} 记一行摘要字符串
     * </ul>
     *
     * <p>两处判定的差异不在清单本身，而在个别类型是否保留原文（见 {@link #TEXT_BODY_MIME_TYPES}），
     * 共用清单可避免新增类型时两处漏改其一
     */
    private static final Set<FileTypeEnum> FILE_DOWNLOAD_TYPES = EnumSet.of(
            FileTypeEnum.PDF,
            FileTypeEnum.DOC,
            FileTypeEnum.XLS,
            FileTypeEnum.PPT,
            FileTypeEnum.DOCX,
            FileTypeEnum.XLSX,
            FileTypeEnum.PPTX,
            FileTypeEnum.ZIP,
            FileTypeEnum.SEVEN_ZIP,
            FileTypeEnum.RAR,
            FileTypeEnum.TAR,
            FileTypeEnum.GZ,
            FileTypeEnum.PNG,
            FileTypeEnum.JPG,
            FileTypeEnum.GIF,
            FileTypeEnum.WEBP,
            FileTypeEnum.SVG,
            FileTypeEnum.BMP,
            FileTypeEnum.TIFF,
            FileTypeEnum.MP3,
            FileTypeEnum.WAV,
            FileTypeEnum.OGG,
            FileTypeEnum.MP4,
            FileTypeEnum.MPEG,
            FileTypeEnum.MOV,
            FileTypeEnum.WEBM,
            FileTypeEnum.TTF,
            FileTypeEnum.OTF
    );

    /**
     * 命中文件类清单、但原文仍有归档价值的文本形态 MIME
     *
     * <p>xml/txt 响应常是排障线索：下游透传的错误详情、异常信息都在里面，摘要会丢掉关键内容
     *
     * <p>显式维护而非依赖"这些类型碰巧不在清单里"：将来若把 xml 纳入下载清单，本守卫仍生效，
     * 不会静默变成只记一行摘要
     */
    private static final Set<String> TEXT_BODY_MIME_TYPES = Set.of("text/plain", "application/xml", "text/xml");


    private final int order;
    private final MenuCache menuCache;
    private final Tracer tracer;
    /** 访问日志排除名单（接口路径 / 请求头），支持配置中心刷新 */
    private final AccessLogExcludeProperties logExcludeProperties;

    public EnhancedAccessLogFilter(int order, MenuCache menuCache, Tracer tracer,
                                   AccessLogExcludeProperties logExcludeProperties) {
        this.order = order;
        this.menuCache = menuCache;
        this.tracer = tracer;
        this.logExcludeProperties = logExcludeProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 对外接口、性能测试等请求不记录
        if (isLogExcluded(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        // 入口时刻：耗时用单调钟 nanoTime 求差值；requestTime 仅作审计维度的绝对时间
        long startNanos = System.nanoTime();
        LocalDateTime requestTime = LocalDateTime.now();

        /*
         * WebFlux 中请求体、响应体只能被消费一次，为了把具体请求透传到下游接口，
         * 必须换装成缓存过请求体的装饰器请求，见下方 getResponseDecorator / withCachedRequest
         */
        ServerHttpResponseDecorator decoratedResponse = getResponseDecorator(exchange);

        /*
         * 处理完请求再记录响应信息
         * doOnError 只摘 message 不摘堆栈（堆栈由 GlobalWebExceptionHandler 按其级别打印，此处不重复），
         * doFinally 覆盖 complete/error/cancel 三种信号，见类注释
         */
        Supplier<? extends Mono<Void>> supplier = () -> chain
                .filter(withCachedRequest(exchange, decoratedResponse))
                .doOnError(error -> exchange.getAttributes().put(CACHED_ERROR_MESSAGE_KEY, error.getMessage()))
                .doFinally(signalType -> saveAccessLog(exchange, requestTime, startNanos));

        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        /*
         * 文件表单不缓存请求体：缓存意味着把整个上传文件读进网关内存（GB 级上传就是自造 OOM），
         * 只记一次"上传文件事件及其体积"，见 resolveRequestBody 的 multipart 分支
         */
        if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
            return supplier.get();
        }

        // JSON：只采样上限范围内的字节，超限降级为体积摘要
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (cachedRequest) ->
                    readBodySample(cachedRequest)
                            .doOnNext(bytes -> exchange.getAttributes().put(CACHED_REQUEST_BODY_JSON_KEY,
                                    isTruncated(bytes)
                                            ? REQUEST_BODY_OVER_LIMIT.formatted((cachedRequest.getHeaders().getContentLength()))
                                            : new String(bytes, StandardCharsets.UTF_8)))
                            .then()).then(Mono.defer(supplier));
        }

        // 普通表单：超限不写 exchange 属性（解析了一半的表单，没意义），由读取侧统一输出体积摘要
        if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
            return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (cachedRequest) ->
                    readBodySample(cachedRequest)
                            .doOnNext(bytes -> {
                                if (!isTruncated(bytes)) {
                                    exchange.getAttributes().put(CACHED_REQUEST_BODY_X_WWW_FORM_URLENCODED_KEY, parseForm(bytes));
                                }
                            })
                            .then()).then(Mono.defer(supplier));
        }

        // 其他 Content-Type 请求
        return supplier.get();
    }

    /**
     * 包装响应装饰器，在写回前拦截响应体：SCG 转发后的报文只在 {@code writeWith} 阶段产生，此刻不拷贝，后续再无机会取到
     *
     * <p>缓存侧超限裁剪，但写回下游的仍是完整字节，裁剪只影响审计日志，不影响响应本身
     */
    private ServerHttpResponseDecorator getResponseDecorator(ServerWebExchange exchange) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory responseBufferFactory = originalResponse.bufferFactory();
        return new ServerHttpResponseDecorator(originalResponse) {
            @Override
            @SuppressWarnings("NullableProblems")
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                /*
                 * 文件下载判定基于响应头、与 body 类型无关，
                 * 优先判断才能对 Flux / Mono 两种形态统一写缓存响应体占位, 不缓存内容
                 */
                if (isFileDownload(exchange)) {
                    exchange.getAttributes().put(CACHED_RESPONSE_BODY_KEY, RESPONSE_BODY_OK_FILE_DOWNLOAD);
                    return super.writeWith(body);
                }

                /*
                 * 网关转发链路 NettyRoutingFilter 写下游响应的 body 必然是 Flux<DataBuffer>
                 * 非 Flux（Mono 单个 buffer 直写）只可能出现在网关本地直接写响应、绕过转发的路径上
                 * 这类响应没有路由 id、没有操作名，本身审计价值就低；原样透传，不参与缓存
                 */
                if (!(body instanceof Flux<? extends DataBuffer> fluxBody)) {
                    return super.writeWith(body);
                }

                return super.writeWith(fluxBody.buffer().flatMap(dataBuffers -> {
                    byte[] bytes = mergeBuffers(dataBuffers);
                    // 响应体写入 exchange 属性（超长只存摘要）
                    exchange.getAttributes().put(CACHED_RESPONSE_BODY_KEY, toCachedBody(bytes));
                    // 下发完整报文：缓存可以裁剪，响应不可以，这里必须是未经裁剪的 bytes
                    return Mono.just(responseBufferFactory.wrap(bytes));
                }));
            }
        };
    }

    /**
     * 组装向下传递的 exchange：响应体装饰器 + 请求体装饰器（若已缓存）
     *
     * <p>为何必须自己换装：{@link ServerWebExchangeUtils#cacheRequestBodyAndRequest} 只把包装后的请求
     * 放进属性 {@code cachedServerHttpRequestDecorator}，不改写 exchange 上的 request
     *
     * <p>真正换装的是内置 {@link AdaptCachedBodyGlobalFilter}（首个分支即"读该属性 → 移除 →
     * {@code mutate().request(decorator)}"）。若依赖它，本过滤器必须更早执行；顺序一反属性还没写入，
     * 请求体已被 {@code NettyRoutingFilter} 消费，下游按 {@code Content-Length} 等字节数、网关等下游响应，
     * 双方互等 → ReadTimeoutException → 504，表象是"请求调不到下游"而非可见的 4xx。自己换装后
     * order 不再受约束，两种排布都成立
     *
     * <p>{@code mutate()} 不丢属性：{@code build()} 返回 {@code MutativeDecorator}，attributes 委托给原
     * exchange，下游写入的属性（如路由 id）在 doFinally 里仍可读到
     *
     * <p>未走缓存的分支（multipart、其它 Content-Type）读不到该属性，原样返回
     *
     * @param exchange          当前请求上下文，不能为 null
     * @param decoratedResponse 响应装饰器，不能为 null
     * @return 换装后的 exchange，恒不为 null
     */
    private static ServerWebExchange withCachedRequest(ServerWebExchange exchange,
                                                       ServerHttpResponseDecorator decoratedResponse) {
        ServerWebExchange.Builder builder = exchange.mutate().response(decoratedResponse);
        ServerHttpRequest cachedRequest = exchange.getAttribute(
                ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
        if (cachedRequest != null) {
            builder.request(cachedRequest);
        }
        return builder.build();
    }

    /**
     * 组装并记录一条访问日志
     *
     * @param exchange    当前请求上下文，不能为 null
     * @param requestTime 请求进入网关的墙钟时间，不能为 null
     * @param startNanos  请求进入时的单调钟读数，用于算耗时
     */
    private void saveAccessLog(ServerWebExchange exchange, LocalDateTime requestTime, long startNanos) {
        ServerHttpRequest request = exchange.getRequest();
        EnhancedAccessLog accessLog = new EnhancedAccessLog();

        // 目标服务与 HTTP 状态码：请求异常中止、状态码尚未写入时为 null
        accessLog.setServiceName(serviceName(exchange));
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        accessLog.setHttpStatus(statusCode != null ? statusCode.value() : null);

        // 请求侧信息
        MediaType mediaType = request.getHeaders().getContentType();
        accessLog.setRequestMethod(request.getMethod().name());
        accessLog.setRequestPath(request.getPath().value());
        accessLog.setRequestContentType(mediaType != null ? mediaType.toString() : null);

        // 时间与耗时
        accessLog.setRequestTime(requestTime);
        accessLog.setResponseTime(LocalDateTime.now());
        accessLog.setExecuteTime(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());

        // 操作人快照：三者同为 null 即匿名请求（放行名单、鉴权失败），刻意不用 0 占位
        LoginUserInfo loginUser = exchange.getAttribute(LoginUserSnapshotFilter.LOGIN_USER_SNAPSHOT_KEY);
        if (loginUser != null) {
            accessLog.setOperatorName(loginUser.getRealName());
            accessLog.setOperatorEmployeeNo(loginUser.getUsername());
        }

        /*
         * 客户端环境
         * IP 只认 TCP 对端，不采信 XFF（XFF 原文另存供排查）
         * 后续有可信反代的话, 再从 XFF 取
         */
        accessLog.setClientIp(clientIp(request));
        accessLog.setForwardedFor(request.getHeaders().getFirst(X_FORWARDED_FOR));
        accessLog.setUserAgent(request.getHeaders().getFirst(HttpHeaders.USER_AGENT));

        // 操作语义：中文操作名（菜单标题链），未命中菜单时为 null
        MenuCacheSnapshot snapshot = menuCache.getSnapshot();
        if (snapshot != null) {
            accessLog.setOperation(snapshot.lookupDesc(request.getPath().value()));
        }

        /*
         * 请求、响应报文先脱敏再裁剪，顺序颠倒会影响数据
         * 请求体是脱敏前的数据，响应体大部分情况已是脱敏后的数据，但不能 100% 排除
         * 于是都过一遍 脱敏 → 裁剪
         */
        Map<String, Object> requestBody = toBodyMap(maskSensitive(resolveRequestBody(exchange, request, mediaType)), mediaType);
        Map<String, Object> responseBody = toBodyMap(maskSensitive(exchange.getAttribute(CACHED_RESPONSE_BODY_KEY)), mediaType);

        // 业务状态码：取自响应体 code，是排查大查询失败的关键；响应非 ApiResult 形状时留空，由前端回退 HTTP 状态码
        Integer bizCode = null;
        if (responseBody != null && responseBody.get(ApiResult.Fields.code) instanceof Number code) {
            // 只认数字形态：字符串、裁剪占位符一律留空，不猜，避免把"没读到"渲染成 0
            bizCode = code.intValue();
        }
        accessLog.setBizCode(bizCode);
        accessLog.setRequestBody(requestBody);
        accessLog.setResponseBody(responseBody);
        accessLog.setErrorMsg(errorMessage(exchange));

        /*
         * 若后续大响应成为常态，可把 saveUserActivityLog 整体挪到 Schedulers.boundedElastic() 或线程池时
         * 需要注意链路context 是否已随终止信号丢失, 丢失后新线程上取不到 traceId: tracer.currentSpan()
         */
        accessLog.setTraceId(tracer.currentSpan().context().traceId());

        log.info("[增强型访问日志]{}", JacksonUtil.toJsonStr(accessLog));
    }

    /**
     * 取待记录的请求体（GET 为查询参数拼成的对象），各分支与响应体同一口径：超过上限只记体积
     *
     * <p>表单与 JSON 分支读取后即从 exchange 移除缓存属性，避免大对象滞留到请求结束
     *
     * @param exchange  当前请求上下文，不能为 null
     * @param request   当前请求，不能为 null
     * @param mediaType 请求 Content-Type，无请求体时为 null
     * @return 待记录的请求体文本；未缓存的场景返回体积摘要或占位提示，恒不为 null
     */
    private String resolveRequestBody(ServerWebExchange exchange, ServerHttpRequest request, MediaType mediaType) {
        // GET 请求：查询参数拼成对象
        if (HttpMethod.GET.matches(request.getMethod().name())) {
            return toParamsJson(request.getQueryParams());
        }

        // 普通表单
        if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)) {
            MultiValueMap<String, String> form = exchange.getAttribute(CACHED_REQUEST_BODY_X_WWW_FORM_URLENCODED_KEY);
            exchange.getAttributes().remove(CACHED_REQUEST_BODY_X_WWW_FORM_URLENCODED_KEY);
            return form != null
                    ? toParamsJson(form)
                    : REQUEST_BODY_OVER_LIMIT.formatted((request.getHeaders().getContentLength()));
        }

        // 文件表单：请求体不缓存，只有请求头里的体积可用（分块传输为 -1）
        if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType)) {
            long contentLength = request.getHeaders().getContentLength();
            return REQUEST_BODY_MULTIPART_NOT_CACHED.formatted(contentLength);
        }

        // JSON 请求体；其余 Content-Type 未缓存，记"未找到"占位
        String body = exchange.getAttribute(CACHED_REQUEST_BODY_JSON_KEY);
        exchange.getAttributes().remove(CACHED_REQUEST_BODY_JSON_KEY);
        return body != null ? body : REQUEST_BODY_NOT_FOUND;
    }

    @Override
    public int getOrder() {
        return order;
    }

    // =========== 内部辅助方法 ===========

    /**
     * 是否命中日志排除名单：命中任一排除请求头，或路径命中排除名单（精确路径 / 通配符）
     *
     * @param request 当前请求，不能为 null
     * @return 命中排除名单返回 true
     */
    private boolean isLogExcluded(ServerHttpRequest request) {
        return logExcludeProperties.getHeaders().stream().anyMatch(header -> request.getHeaders().containsKey(header))
                || logExcludeProperties.getPathPatterns().stream().anyMatch(pattern -> pattern.matches(request.getPath()));
    }

    /**
     * 采样请求体前 {@link #BODY_SAMPLE_BYTES} 字节
     *
     * <p>不走 {@code ServerRequest.bodyToMono}：解码器内部 {@code join(...)} 出来的 buffer 没人释放，
     * 释放责任在订阅方，只有自己读、自己 release 才能配平；
     * {@link DataBufferUtils#takeUntilByteCount} 本身无释放义务，它读到上限时自行释放被丢弃的尾部，
     * 故这里对每个收到的 buffer 释放一次即可
     *
     * @param request 待采样的请求，不能为 null
     * @return 采样的字节；空请求体返回空数组
     */
    private static Mono<byte[]> readBodySample(ServerHttpRequest request) {
        return DataBufferUtils.takeUntilByteCount(request.getBody(), BODY_SAMPLE_BYTES)
                .collectList()
                .map(EnhancedAccessLogFilter::copyAndRelease)
                .defaultIfEmpty(EMPTY_BYTES);
    }

    /**
     * 拷贝字节并逐个释放 {@link DataBuffer}
     *
     * <p>与响应侧 {@link #mergeBuffers} 同一手法；区别是本处带 try/finally，采样中途异常也保证释放
     *
     * @param buffers 待拷贝的 buffer，不能为 null
     * @return 合并后的字节
     */
    private static byte[] copyAndRelease(List<DataBuffer> buffers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (DataBuffer buffer : buffers) {
            try {
                byte[] chunk = new byte[buffer.readableByteCount()];
                buffer.read(chunk);
                out.write(chunk, 0, chunk.length);
            } finally {
                DataBufferUtils.release(buffer);
            }
        }
        return out.toByteArray();
    }

    /**
     * 采样是否被截断：取满 {@link #BODY_SAMPLE_BYTES} 即说明原始报文超过 {@link #MAX_CACHED_BODY_BYTES}
     *
     * @param sample 采样结果，不能为 null
     * @return 被截断返回 true
     */
    private static boolean isTruncated(byte[] sample) {
        return sample.length >= BODY_SAMPLE_BYTES;
    }


    /**
     * 解析表单报文：按 {@code &} 切分并逐项 URL 解码
     *
     * @param sample 采样到的表单字节，不能为 null
     * @return 表单字段；空报文返回空 map，不为 null
     */
    private static MultiValueMap<String, String> parseForm(byte[] sample) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (sample.length == 0) {
            return form;
        }
        String[] pairs = new String(sample, StandardCharsets.UTF_8).split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            form.add(decodeFormValue(pair.substring(0, separator < 0 ? pair.length() : separator)),
                    separator < 0 ? "" : decodeFormValue(pair.substring(separator + 1)));
        }
        return form;
    }

    /**
     * 解码单个表单项；非法转义会抛异常，记日志不能把请求带崩，故降级为原样保留
     *
     * @param raw 原始值，不能为 null
     * @return 解码后的值，解码失败返回原值
     */
    private static String decodeFormValue(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /**
     * 是否文件下载：Content-Disposition 声明 attachment，或响应 Content-Type 命中文件类清单
     *
     * <p>判定基于响应头、与 body 形态无关，故对 Flux / Mono 两种写法都能统一处理
     *
     * @param exchange 当前请求上下文，不能为 null
     * @return 是文件下载返回 true
     */
    private static boolean isFileDownload(ServerWebExchange exchange) {
        String contentDisposition = exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDisposition != null && contentDisposition.toLowerCase().contains("attachment")) {
            // 明确指示为附件下载
            return true;
        }
        // 或按响应 Content-Type 命中文件类清单
        return findFileType(exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR)) != null;
    }

    /**
     * 合并响应体的全部字节并释放原始 {@link DataBuffer}
     *
     * <p>必须合并后一次性解码：逐块 {@code new String(chunk, UTF_8)} 会把被切在两个 buffer 之间的
     * 多字节字符（中文、emoji）解成乱码
     *
     * @param dataBuffers 响应体的 buffer 列表，不能为 null
     * @return 合并后的字节
     */
    private static byte[] mergeBuffers(List<? extends DataBuffer> dataBuffers) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (DataBuffer dataBuffer : dataBuffers) {
            byte[] chunk = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(chunk);
            all.write(chunk, 0, chunk.length);
            DataBufferUtils.release(dataBuffer);
        }
        return all.toByteArray();
    }

    /**
     * 生成待记录的响应体：未超限记原文，超限只保留根层 code / msg，data 记体积说明
     *
     * <p>超限时丢 code 会让业务状态码缺失，故用流式挑出根层标量、不建树，内存与报文长度无关
     *
     * @param bytes 响应体字节，不能为 null
     * @return 待记录的响应体文本；超限且挑不出标量时返回占位提示
     */
    private static String toCachedBody(byte[] bytes) {
        if (bytes.length > MAX_CACHED_BODY_BYTES) {
            Map<String, Object> summary = JacksonUtil.convertValue(
                    JacksonUtil.parseRootScalars(bytes, RESPONSE_SUMMARY_FIELDS), BODY_MAP_TYPE);

            if (summary == null || summary.isEmpty()) {
                return RESPONSE_BODY_NOT_FOUND;
            }
            if (!summary.containsKey(ApiResult.Fields.msg)) {
                summary.put(ApiResult.Fields.msg, RESPONSE_OVER_LIMIT_MSG);
            }
            summary.put(ApiResult.Fields.data, RESPONSE_OVER_LIMIT_DATA.formatted(bytes.length));
            return JacksonUtil.toJsonStr(summary);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 报文脱敏：先 JSON 键值脱敏，再做全文文本脱敏
     *
     * <p>顺序有讲究，不能颠倒：先按 JSON 键整值替换，不破坏结构（零残留）；再对剩余明文做文本脱敏；
     * 反过来「先文本后键值」会残留形似手机号的密文
     *
     * <p>JSON 解析失败 / 没有凭据键名时原样返回，由文本脱敏兜底
     *
     * @param body 待脱敏的报文，可为 null
     * @return 脱敏后的报文；入参为 null 时返回 null
     */
    private static String maskSensitive(String body) {
        return body == null ? null : SensitiveTextUtil.mask(SensitiveJsonUtil.mask(body));
    }

    /**
     * 报文转结构化 Map：JSON 走裁剪后解析，非 JSON 按统一形态归档
     *
     * <p>先判首字符再决定是否裁剪，而不是无条件丢给 JSON 工具类：首字符判定成本极低，
     * 可完全避开工具类的重量级路径
     *
     * @param body        报文文本，可为 null
     * @param contentType 报文的 Content-Type，用于判定是否二进制归档，可为 null
     * @return 结构化结果；报文为空时返回 null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toBodyMap(String body, MediaType contentType) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        if (!looksLikeJson(body)) {
            return toNonJsonBody(body, contentType);
        }
        try {
            Object parsed = JacksonUtil.parseObject(JacksonUtil.pruneJson(body), STRUCTURED_BODY_TYPE);
            return parsed instanceof Map<?, ?> map
                                ? (Map<String, Object>) map
                                : Map.of(NON_JSON_FIELD_RAW, parsed);

        } catch (RuntimeException e) {
            // 半截 JSON（如被字节截断的报文）由 try/catch 兜底为文本归档，旁路日志不能拖垮请求
            return toNonJsonBody(body, contentType);
        }
    }

    /**
     * 是否疑似 JSON：只看首个非空白字符
     *
     * <p>不是为了严格校验：合法 JSON 一定以 {@code {} 或 []} 开头，反之不成立，
     * 判定为 JSON 的后续仍有 try/catch 兜底，成本可控
     *
     * @param body 报文文本，不能为 null
     * @return 首个非空白字符是 JSON 起始符返回 true
     */
    private static boolean looksLikeJson(String body) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == '{' || c == '[';
            }
        }
        return false;
    }

    /**
     * 非 JSON 报文的统一归档形态：{@code {"_raw":"<字符串>"}} 单字段
     *
     * <p>文本 / HTML 存截断原文：HTML 错误页、纯文本报错往往是排障线索，
     * 例如网关把下游的 HTML 错误页原样透传时，页面里往往带着失败原因
     *
     * <p>二进制（图片、音视频、字体、压缩包、PDF 等）存一行摘要字符串："什么、多大"
     *
     * @param body        报文文本，不能为 null
     * @param contentType 响应 Content-Type，用于判定是否二进制，可为 null
     * @return 单字段 map，恒不为 null
     */
    private static Map<String, Object> toNonJsonBody(String body, MediaType contentType) {
        String raw = isBinaryBody(contentType)
                        ? BINARY_SUMMARY_MARK.formatted(contentType, body.length())
                        : abbreviate(body);
        return Map.of(NON_JSON_FIELD_RAW, raw);
    }


    /**
     * 是否"原文无归档价值"的二进制响应：Content-Type 命中文件类清单且不在文本保留名单中，
     * 无 Content-Type 视为文本
     *
     * @param contentType 响应 Content-Type，可为 null
     * @return 是二进制返回 true
     */
    private static boolean isBinaryBody(MediaType contentType) {
        FileTypeEnum type = findFileType(contentType != null ? contentType.toString() : null);
        return type != null && !TEXT_BODY_MIME_TYPES.contains(type.getMimeType());
    }

    /**
     * 从 Content-Type 匹配"网关认定的文件类"类型：先按 {@link FileTypeEnum} 全字典反查，
     * 再看是否落在 {@link #FILE_DOWNLOAD_TYPES} 子集内；非文件类（含无 Content-Type）返回 null
     *
     * @param contentType Content-Type 文本，可为 null
     * @return 命中的文件类型；非文件类返回 null
     */
    private static FileTypeEnum findFileType(String contentType) {
        FileTypeEnum type = FileTypeEnum.fromMimeTypeOrNull(normalizeMimeType(contentType));
        return type != null && FILE_DOWNLOAD_TYPES.contains(type) ? type : null;
    }

    /**
     * 剥离 Content-Type 的参数部分（{@code ;charset=UTF-8}），只留裸 MIME
     *
     * @param contentType Content-Type 文本，可为 null
     * @return 裸 MIME；入参为 null 或空白返回 null
     */
    private static String normalizeMimeType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String normalized = semicolon > 0 ? contentType.substring(0, semicolon).trim() : contentType.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 截断纯文本到 {@link #MAX_CACHED_BODY_BYTES}，避免单条审计日志过大
     *
     * @param body 原文，不能为 null
     * @return 截断后的文本，超长时带 {@code ...} 后缀
     */
    private static String abbreviate(String body) {
        return body.length() <= MAX_CACHED_BODY_BYTES
                ? body
                : body.substring(0, MAX_CACHED_BODY_BYTES) + "...";
    }

    /**
     * 目标微服务名：优先取命中路由 URI 的 host，取不到回退路由 ID
     *
     * <p>host 在不同 URI 形态下的含义：
     * <ul>
     *   <li>{@code lb://yeed-auth}：host 即 Nacos 注册服务名
     *   <li>{@code http://ip:port}：host 是直连地址
     *   <li>{@code forward:/xx}：host 恒为 null，回退路由 ID
     * </ul>
     *
     * <p>未命中路由（404 等）两者皆 null
     *
     * @param exchange 当前请求上下文，不能为 null
     * @return 服务名或路由 ID；都取不到时返回 null
     */
    private static String serviceName(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI uri = route != null ? route.getUri() : null;
        String host = uri != null ? uri.getHost() : null;
        return host != null ? host
                : exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR);
    }

    /**
     * 客户端 IP：只认 TCP 连接对端地址，不采信 X-Forwarded-For
     *
     * <p>代理链中它是最近一跳的真实 IP，且无法被客户端伪造；XFF 原文另存供排查
     *
     * @param request 当前请求，不能为 null
     * @return 对端 IP；取不到返回 null
     */
    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : null;
    }

    /**
     * 网关层异常信息：只取 message 不取堆栈
     *
     * <p>异常消息可能携带下游响应体片段，故过文本脱敏；正常请求无该属性，返回 null
     *
     * @param exchange 当前请求上下文，不能为 null
     * @return 脱敏后的异常摘要；无异常返回 null
     */
    private static String errorMessage(ServerWebExchange exchange) {
        String message = exchange.getAttribute(CACHED_ERROR_MESSAGE_KEY);
        return message == null ? null : SensitiveTextUtil.mask(message);
    }

    /**
     * 把查询参数 / 表单字段拼成 JSON，供后续统一脱敏与裁剪
     *
     * <p>不含文件表单：{@link #filter} 不缓存其请求体，没有字段可提取
     *
     * @param params 查询参数或表单字段，不能为 null
     * @return 拼成的 JSON 文本
     */
    private static String toParamsJson(MultiValueMap<String, String> params) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            List<String> values = entry.getValue();
            // 单值存标量、多值存数组：与 JSON 报文里的同名字段保持同一形态，前端渲染不用分支
            fields.put(entry.getKey(), values.size() == 1 ? values.getFirst() : values);
        }
        return JacksonUtil.toJsonStr(fields);
    }


    /**
     * 一条增强型访问日志的结构化载体
     *
     * <p>字段按"链路 / 请求 / 响应 / 目标服务 / 操作人 / 客户端"分组，序列化成一行 JSON 落日志
     */
    @Data
    @Accessors(chain = true)
    public static class EnhancedAccessLog {
        // ==================== 链路追踪 ====================
        /** 全链路追踪 ID */
        private String traceId;

        // ==================== 请求 ====================
        /** 请求进入网关的时间 */
        private LocalDateTime requestTime;
        /** 请求方法（GET / POST / PUT / DELETE） */
        private String requestMethod;
        /** 请求路径（不含 query 参数） */
        private String requestPath;
        /** 请求 Content-Type（如 application/json）；无请求体时为 null */
        private String requestContentType;
        /** 请求体；文件上传、超限等场景为摘要对象；根层是数组 / 标量时包成单键对象 */
        private Map<String, Object> requestBody;

        // ==================== 响应 ====================
        /** 响应写回网关的时间 */
        private LocalDateTime responseTime;
        /** HTTP 状态码；请求异常中止、状态码尚未写入时为 null */
        private Integer httpStatus;
        /** 下游业务状态码；响应非 ApiResult 形状时为 null */
        private Integer bizCode;
        /** 响应体；文件下载、超限等场景为摘要对象；根层是数组 / 标量时包成单键对象 */
        private Map<String, Object> responseBody;
        /** 网关层异常摘要；正常请求为 null */
        private String errorMsg;

        // ==================== 目标服务与耗时 ====================
        /** 目标微服务名 */
        private String serviceName;
        /** 执行耗时（毫秒），覆盖整个下游转发 + 响应阶段 */
        private Long executeTime;

        // ==================== 操作人快照 ====================
        /** 操作人姓名 */
        private String operatorName;
        /** 操作人工号 */
        private String operatorEmployeeNo;

        // ==================== 客户端环境 ====================
        /** 客户端 IP */
        private String clientIp;
        /** 原始 X-Forwarded-For 请求头 */
        private String forwardedFor;
        /** 客户端 User-Agent 原文 */
        private String userAgent;
        /** 中文操作名（菜单标题链，如「系统管理/角色管理/角色查询」） */
        private String operation;

    }

}
