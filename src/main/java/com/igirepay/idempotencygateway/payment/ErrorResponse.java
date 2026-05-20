package com.igirepay.idempotencygateway.payment;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        List<String> messages
) {
}
