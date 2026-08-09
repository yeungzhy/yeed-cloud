package com.yeungzhy.yeed.common.config;

import com.yeungzhy.yeed.common.bootstrap.StartupInfoPrinter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用启动信息打印器配置
 *
 * <p>通过 {@code @Bean} 注册 {@link StartupInfoPrinter}，本配置类由
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动加载，下游模块引入 yeed-common 即生效。
 *
 * @author yeungzhy
 * @date 2026-08-09
 */
@Configuration
public class StartupInfoPrinterConfig {

    @Bean
    public StartupInfoPrinter startupInfoPrinter() {
        return new StartupInfoPrinter();
    }

}
