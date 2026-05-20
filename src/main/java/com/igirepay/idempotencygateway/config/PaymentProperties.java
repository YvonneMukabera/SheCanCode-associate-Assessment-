package com.igirepay.idempotencygateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private Duration processingDelay = Duration.ofSeconds(2);

    public Duration getProcessingDelay() {

        return processingDelay;
    }

    public void setProcessingDelay(Duration processingDelay) {

        this.processingDelay = processingDelay;
    }
}
