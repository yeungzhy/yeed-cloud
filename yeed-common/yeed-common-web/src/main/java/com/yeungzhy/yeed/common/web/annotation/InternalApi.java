package com.yeungzhy.yeed.common.web.annotation;

import com.yeungzhy.yeed.common.web.exception.InternalApiExceptionHandler;

import java.lang.annotation.*;

/**
 * 标记"内部服务间 Feign 专用端点"（仅对内暴露，网关不路由该前缀）。
 *
 * <p>内部 RPC 返回裸数据、失败走异常的错误映射约定：标注本注解的
 * Controller 抛出的任何异常由 {@link InternalApiExceptionHandler}
 * 统一转为 HTTP 错误码 + ApiResult body（成功仍返回裸数据 / HTTP 200），
 *
 * <p>与对外端点"HTTP 200 + 业务码"的契约区分开：
 * <ul>
 *   <li>对外端点：业务失败返回 HTTP 200 + {@code ApiResult.error}，前端判 {@code code}；</li>
 *   <li>内部端点：业务失败抛异常 → 映射 HTTP 错误码，Feign 消费方经
 *       {@code ErrorDecoder} 解析 body 中的 code/msg 还原为 {@code BizException}。</li>
 * </ul>
 *
 * <p>为什么内部 RPC 返回裸数据失败必须非 2xx：Feign 对 2xx 一律走 Decoder 尝试反序列化为返回类型，
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
