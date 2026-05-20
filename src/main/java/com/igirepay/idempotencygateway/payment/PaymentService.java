package com.igirepay.idempotencygateway.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.igirepay.idempotencygateway.config.IdempotencyProperties;
import com.igirepay.idempotencygateway.config.PaymentProperties;

@Service
public class PaymentService {

    private static final String DIFFERENT_BODY_MESSAGE =
            "Idempotency key already used for a different request body.";

    private final Map<String, IdempotencyEntry> idempotencyStore = new ConcurrentHashMap<>();
    private final PaymentProperties paymentProperties;
    private final IdempotencyProperties idempotencyProperties;
    private final Clock clock;
    private final AtomicInteger processedChargeCount = new AtomicInteger();

    @Autowired
    public PaymentService(
            PaymentProperties paymentProperties,
            IdempotencyProperties idempotencyProperties
    ) {
        this(paymentProperties, idempotencyProperties, Clock.systemUTC());
    }

    PaymentService(
            PaymentProperties paymentProperties,
            IdempotencyProperties idempotencyProperties,
            Clock clock
    ) {
        this.paymentProperties = paymentProperties;
        this.idempotencyProperties = idempotencyProperties;
        this.clock = clock;
    }

    public PaymentProcessingResult processPayment(
            String idempotencyKey,
            String requestBodyHash,
            PaymentRequest request
    ) {
        IdempotencyEntry newEntry = new IdempotencyEntry(
                requestBodyHash,
                Instant.now(clock),
                new CompletableFuture<>()
        );

        IdempotencyEntry existingEntry = idempotencyStore.putIfAbsent(idempotencyKey, newEntry);

        if (existingEntry == null) {
            return processFirstRequest(idempotencyKey, request, newEntry);
        }

        if (!existingEntry.requestBodyHash().equals(requestBodyHash)) {
            throw new IdempotencyConflictException(DIFFERENT_BODY_MESSAGE);
        }

        CachedPaymentResponse cachedResponse = existingEntry.response().join();
        return new PaymentProcessingResult(cachedResponse.status(), cachedResponse.body(), true);
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void removeExpiredKeys() {
        Instant cutoff = Instant.now(clock).minus(idempotencyProperties.getRetention());
        idempotencyStore.entrySet().removeIf(entry ->
                entry.getValue().createdAt().isBefore(cutoff) && entry.getValue().response().isDone()
        );
    }

    int processedChargeCount() {
        return processedChargeCount.get();
    }

    private PaymentProcessingResult processFirstRequest(
            String idempotencyKey,
            PaymentRequest request,
            IdempotencyEntry entry
    ) {
        try {
            simulatePaymentProcessor();
            processedChargeCount.incrementAndGet();

            PaymentResponse response = new PaymentResponse(
                    "Charged " + formatAmount(request.amount()) + " " + request.currency()
            );
            CachedPaymentResponse cachedResponse = new CachedPaymentResponse(HttpStatus.CREATED, response);
            entry.response().complete(cachedResponse);

            return new PaymentProcessingResult(cachedResponse.status(), cachedResponse.body(), false);
        } catch (RuntimeException exception) {
            idempotencyStore.remove(idempotencyKey, entry);
            entry.response().completeExceptionally(exception);
            throw exception;
        }
    }

    private void simulatePaymentProcessor() {
        try {
            Thread.sleep(paymentProperties.getProcessingDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment processing was interrupted.", exception);
        }
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
