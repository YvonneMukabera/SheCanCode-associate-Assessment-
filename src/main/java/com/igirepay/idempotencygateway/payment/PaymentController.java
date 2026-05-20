package com.igirepay.idempotencygateway.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PaymentController(PaymentService paymentService, ObjectMapper objectMapper, Validator validator) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/process-payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode requestBody
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key header is required.");
        }

        PaymentRequest request = convertAndValidate(requestBody);
        String requestBodyHash = hashCanonicalJson(requestBody);

        PaymentProcessingResult result = paymentService.processPayment(
                idempotencyKey.trim(),
                requestBodyHash,
                request
        );

        return ResponseEntity.status(result.status())
                .header("X-Cache-Hit", Boolean.toString(result.cacheHit()))
                .body(result.body());
    }

    private PaymentRequest convertAndValidate(JsonNode requestBody) {
        PaymentRequest request = objectMapper.convertValue(requestBody, PaymentRequest.class);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "paymentRequest");
        validator.validate(request, bindingResult);

        if (bindingResult.hasErrors()) {
            String reason = String.join("; ", bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage() == null
                            ? "Invalid payment request."
                            : error.getDefaultMessage())
                    .toList());
            throw new ResponseStatusException(BAD_REQUEST, reason);
        }

        return request;
    }

    private String hashCanonicalJson(JsonNode requestBody) {
        try {
            byte[] canonicalJson = objectMapper.writeValueAsBytes(requestBody);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid JSON request body.", exception);
        } catch (NoSuchAlgorithmException exception) {
            byte[] fallback = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(fallback);
        }
    }
}
