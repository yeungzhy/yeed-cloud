package com.yeungzhy.yeed.common.web.annotation;

import com.yeungzhy.yeed.common.web.exception.InternalApiExceptionHandler;

import java.lang.annotation.*;

/**
 * 标记"内部服务间 Feign 专用端点"（仅对内暴露，网关不路由该前缀）。
 *
 * <p>RPC-Style（裸数据 + 异常）：标注本注解的 Controller 成功时返回裸数据 / HTTP 200，
 * 抛出的任何异常由 {@link InternalApiExceptionHandler}
 * 统一转为 HTTP 错误码 + ApiResult body。
 *
 * <p>与对外端点的 RESTful-Style（统一封装 + 业务码）是两种相反的契约：
 * <ul>
 *   <li>RESTful-Style（对外端点）：业务失败返回 HTTP 200 + {@code ApiResult.error}，前端判 {@code code}；</li>
 *   <li>RPC-Style（内部端点）：业务失败抛异常 → 映射 HTTP 错误码，Feign 消费方经
 *       {@code ErrorDecoder} 解析 body 中的 code/msg 还原为 {@code BizException}。</li>
 * </ul>
 *
 * <p>为什么 RPC-Style 失败必须非 2xx：Feign 对 2xx 一律走 Decoder 尝试反序列化为返回类型，
 * 若内部端点失败仍回 HTTP 200 + ApiResult.error，裸数据契约会把错误体解码成"字段全 null
 * 的假成功对象"，静默吞掉错误。
 *
 * @author yeungzhy
 * @see InternalApiExceptionHandler
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InternalApi {

}
