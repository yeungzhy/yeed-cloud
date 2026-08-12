package com.yeungzhy.yeed.common.web.config;

import com.yeungzhy.yeed.common.web.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 全局异常处理自动配置。
 *
 * <p>注册 {@link GlobalExceptionHandler}，将 Controller 层抛出的业务/系统异常统一转换为
 * 标准响应结构并记录日志。
 *
 * @author yeungzhy
 * @since 2026-08-07
 * @see GlobalExceptionHandler
 */
@AutoConfiguration
public class GlobalExceptionAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

}
