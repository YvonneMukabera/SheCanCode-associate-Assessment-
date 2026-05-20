package com.igirepay.idempotencygateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.idempotency")
public class IdempotencyProperties {

    private Duration retention = Duration.ofHours(24);

    public Duration getRetention() {

        return retention;
    }

    public void setRetention(Duration retention) {

        this.retention = retention;
    }
}
