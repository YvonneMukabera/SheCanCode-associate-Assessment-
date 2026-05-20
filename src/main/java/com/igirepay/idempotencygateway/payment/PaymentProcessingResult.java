package com.igirepay.idempotencygateway.payment;

import org.springframework.http.HttpStatus;

public record PaymentProcessingResult(
        HttpStatus status,
        PaymentResponse body,
        boolean cacheHit
) {
}
