package com.yeungzhy.yeed.common.config;

import com.yeungzhy.yeed.common.crypto.BlindIndexProvider;
import com.yeungzhy.yeed.common.crypto.CryptoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    @ConfigurationProperties(prefix = "crypto")
    public CryptoProperties cryptoProperties() {
        // 返回一个空实例，Spring 会自动调用绑定器填充属性
        return new CryptoProperties();
    }

    @Bean
    public BlindIndexProvider idCardBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "idCard");
    }

    @Bean
    public BlindIndexProvider phoneBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "phone");
    }

    @Bean
    public BlindIndexProvider emailBlindIndex(CryptoProperties cryptoProperties) {
        return new BlindIndexProvider(cryptoProperties.getHmac().getSecret(), "email");
    }

}
