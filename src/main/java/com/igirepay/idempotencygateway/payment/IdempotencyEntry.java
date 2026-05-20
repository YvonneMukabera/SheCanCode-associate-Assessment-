package com.igirepay.idempotencygateway.payment;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

record IdempotencyEntry(
        String requestBodyHash,
        Instant createdAt,
        CompletableFuture<CachedPaymentResponse> response
) {
}
