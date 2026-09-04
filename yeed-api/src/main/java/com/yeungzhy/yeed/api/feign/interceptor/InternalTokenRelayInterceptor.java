package com.yeungzhy.yeed.api.feign.interceptor;

import com.yeungzhy.yeed.api.feign.config.InternalFeignConfig;
import com.yeungzhy.yeed.common.core.constant.Constant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 内部 Feign 客户端的请求头透传拦截器：把当前请求上下文中的 token 透传到下游。
 *
 * <p>生效范围：经 {@link InternalFeignConfig} 注册为 <b>Feign 子容器 Bean</b>，只对
 * {@code @FeignClient(configuration = InternalFeignConfig.class)} 的客户端生效；
 * 不是全局 {@code RequestInterceptor}，不会波及其它 Feign 客户端。
 *
 * <p>仅透传当前请求线程的上下文：异步/多线程场景需自行从主线程传递，子线程取不到
 * 请求上下文时跳过（不伪造身份）。
 *
 * @author yeungzhy
 */
public class InternalTokenRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 非 Web 请求上下文（如 job 定时线程）跳过，不伪造身份
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(Constant.TOKEN_HEADER);
        if (StringUtils.hasText(token)) {
            template.header(Constant.TOKEN_HEADER, token);
        }
    }

}
