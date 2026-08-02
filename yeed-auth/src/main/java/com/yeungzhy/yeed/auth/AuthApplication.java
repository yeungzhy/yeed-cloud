package com.yeungzhy.yeed.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务程序入口
 *
 * @author yeungzhy
 * @date 2026-08-02 16:19
 */
@SpringBootApplication
@EnableFeignClients
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

}
