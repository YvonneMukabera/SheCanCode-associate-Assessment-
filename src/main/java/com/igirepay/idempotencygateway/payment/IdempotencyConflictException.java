package com.igirepay.idempotencygateway.payment;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {

        super(message);
    }
}
