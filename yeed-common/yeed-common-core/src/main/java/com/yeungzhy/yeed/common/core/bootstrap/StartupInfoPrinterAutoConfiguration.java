package com.yeungzhy.yeed.common.core.bootstrap;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * StartupInfoPrinter 自动装配
 *
 * <p> 注册 StartupInfoPrinter（监听 ApplicationReadyEvent 打印启动信息、Banner、端口、Profile）。
 * 业务侧可声明同名 Bean 覆盖。
 *
 * <p> 归属说明：StartupInfoPrinter 不依赖 Servlet/MVC 或其他 Web 栈，
 * 使用反射兼容 Servlet / Reactive / 非 Web 三种环境，故放在 common-core，
 * 让 Gateway（Reactive）等所有类型的应用都能共享启动打印能力。
 *
 * @author yeungzhy
 * @since 2026-08-11
 */
@AutoConfiguration
public class StartupInfoPrinterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StartupInfoPrinter startupInfoPrinter() {
        return new StartupInfoPrinter();
    }

}
