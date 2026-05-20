package com.igirepay.idempotencygateway.payment;

import org.springframework.http.HttpStatus;

record CachedPaymentResponse(HttpStatus status, PaymentResponse body) {
}
