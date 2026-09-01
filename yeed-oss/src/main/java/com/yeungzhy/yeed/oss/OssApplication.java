package com.yeungzhy.yeed.oss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * OSS 文件服务启动类
 *
 * @author yeungzhy
 * @since 2026-08-23
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class OssApplication {

    public static void main(String[] args) {
        SpringApplication.run(OssApplication.class, args);
    }

}
