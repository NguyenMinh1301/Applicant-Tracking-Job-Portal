package com.vietrecruit.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;

/**
 * Configuration properties for the PayOS payment gateway client, including credentials, webhook
 * URLs, and log verbosity.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payos")
public class PayOSConfig {

    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String logLevel = "NONE";
    private String returnUrl;
    private String cancelUrl;

    @Bean
    public PayOS payOS() {
        ClientOptions options =
                ClientOptions.builder()
                        .clientId(clientId)
                        .apiKey(apiKey)
                        .checksumKey(checksumKey)
                        .logLevel(ClientOptions.LogLevel.valueOf(logLevel.toUpperCase()))
                        .build();
        return new PayOS(options);
    }
}
