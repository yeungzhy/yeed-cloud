package com.yeungzhy.yeed.common.web.config;

import com.yeungzhy.yeed.common.web.exception.ExternalApiExceptionHandler;
import com.yeungzhy.yeed.common.web.exception.InternalApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 全局异常处理自动配置。
 *
 * <p>注册两个成对存在的 {@code @RestControllerAdvice}，把 Controller 层抛出的异常按
 * <b>端点面向谁</b>转换为两套契约：
 * <ul>
 *   <li>{@link ExternalApiExceptionHandler}：对外端点，HTTP 200 + body 业务码；</li>
 *   <li>{@link InternalApiExceptionHandler}：标注 {@code @InternalApi} 的内部端点，
 *       HTTP 错误码 + ApiResult body，供消费方 Feign 的 ErrorDecoder 还原。</li>
 * </ul>
 *
 * <p>为何必须在此显式装配：两者都在 {@code common-web} 包下，各业务模块的组件扫描边界
 * （{@code com.yeungzhy.yeed.<module>}）扫不到。漏掉 {@link InternalApiExceptionHandler}
 * 的后果尤其隐蔽——内部端点的异常会退化成 HTTP 200：裸数据 Feign 契约下消费方既收不到
 * 错误码也拿不到话术，一律降级为"系统繁忙"，与"下游真的挂了"表现得一模一样。
 * ⇒ 新增任何 {@code @RestControllerAdvice} 都必须同步登记到本类。
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see ExternalApiExceptionHandler
 * @see InternalApiExceptionHandler
 */
@AutoConfiguration
public class ExceptionHandlerAutoConfiguration {

    /** 对外端点异常映射（HTTP 200 + 业务码，兜底所有未被更具体 Advice 接管的异常） */
    @Bean
    public ExternalApiExceptionHandler externalApiExceptionHandler() {
        return new ExternalApiExceptionHandler();
    }

    /** 内部端点异常映射（HTTP 错误码 + ApiResult body，排序见类上 {@code @Order}） */
    @Bean
    public InternalApiExceptionHandler internalApiExceptionHandler() {
        return new InternalApiExceptionHandler();
    }

}
