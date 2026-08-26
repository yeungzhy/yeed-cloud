package com.yeungzhy.yeed.api.feign.interceptor;

import com.yeungzhy.yeed.common.core.constant.Constant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求头透传拦截器：把当前请求上下文中的 token 透传到下游 Feign 调用
 *
 * <p>本拦截器随 yeed-api 自动装配对所有消费方生效，多线程调用请手动显示从主线程传递到子线程
 */
public class FeignTokenRelayInterceptor implements RequestInterceptor {

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
