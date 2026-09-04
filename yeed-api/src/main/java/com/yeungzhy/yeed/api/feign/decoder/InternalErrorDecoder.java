package com.yeungzhy.yeed.api.feign.decoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.common.core.exception.BizException;
import com.yeungzhy.yeed.common.core.result.ApiResult;
import com.yeungzhy.yeed.common.core.support.JacksonUtil;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 内部 Feign 契约的异常解码器（经 {@link InternalFeignConfig} 按客户端装配）。
 *
 * <p>职责：把下游内部端点返回的 <b>HTTP 错误响应</b>（由
 * {@code InternalApiExceptionHandler} 统一映射为 500/400 + ApiResult body）翻译回业务异常：
 * <ul>
 *   <li>响应体为 ApiResult 结构 → 解析 code/msg，抛 {@code BizException(码, msg)}，
 *       业务失败话术（如"账号或密码错误"）经异常链路透传给最终调用方；</li>
 *   <li>响应体不可解析或缺失（网关兜底页等）→ 回落 {@code SYSTEM_ERROR}，不透传内部细节。</li>
 * </ul>
 *
 * <p>边界：本解码器只处理"下游有响应"的错误；连接失败 / 超时 / 无可用实例等未发出或
 * 未收到响应的异常（{@code FeignException.RetryableException} 等）不经 ErrorDecoder，
 * 由消费方全局异常兜底为 {@code SYSTEM_ERROR}——内部依赖故障对用户统一为"系统繁忙"。
 *
 * @author yeungzhy
 * @see ApiResult.CommonCode
 */
@Slf4j
public class InternalErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper JSON_MAPPER = JacksonUtil.newDefaultMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        String bodyText = readBody(response);
        int code = -1;
        String msg = null;
        if (StringUtils.hasText(bodyText)) {
            try {
                JsonNode root = JSON_MAPPER.readTree(bodyText);
                code = root.path(ApiResult.Fields.code).asInt(-1);
                msg = root.path(ApiResult.Fields.msg).asText(null);
            } catch (IOException ignored) {
                // 非 ApiResult 结构（代理兜底页等），回落系统错误
            }
        }
        return new BizException(
                // 将下游响应码映射回 通用业务状态码枚举；未知/缺失时回落 SYSTEM_ERROR(1000)
                ApiResult.CommonCode.CODE_MAP.getOrDefault(code, ApiResult.CommonCode.SYSTEM_ERROR),
                msg
        );
    }

    /** 读取错误响应体；body 为 null（如无响应体的错误状态）返回 null */
    private String readBody(Response response) {
        if (response == null || response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

}
