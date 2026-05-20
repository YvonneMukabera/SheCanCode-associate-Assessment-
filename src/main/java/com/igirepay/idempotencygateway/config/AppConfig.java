package com.igirepay.idempotencygateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PaymentProperties.class, IdempotencyProperties.class})
public class AppConfig {
}
