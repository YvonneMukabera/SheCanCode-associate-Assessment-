package com.igirepay.idempotencygateway.payment;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment.processing-delay=200ms",
        "app.idempotency.retention=1h"
})
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentService paymentService;

    @Test
    void firstRequestProcessesPayment() throws Exception {
        mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"FRW\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Hit", "false"))
                .andExpect(jsonPath("$.message").value("Charged 100 FRW"));
    }

    @Test
    void duplicateRequestReturnsCachedResponseImmediately() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult first = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"FRW\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long start = System.currentTimeMillis();
        MvcResult second = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"FRW\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Hit", "true"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(System.currentTimeMillis() - start).isLessThan(150);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsConflict() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"FRW\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500,\"currency\":\"FRW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messages[0]")
                        .value("Idempotency key already used for a different request body."));
    }

    @Test
    void inFlightDuplicateWaitsForFirstResponseAndOnlyProcessesOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        int before = paymentService.processedChargeCount();

        Callable<MvcResult> request = () -> mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250,\"currency\":\"FRW\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(request);
            Thread.sleep(50);
            Future<MvcResult> second = executor.submit(request);

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();

            assertThat(firstResult.getResponse().getContentAsString())
                    .isEqualTo(secondResult.getResponse().getContentAsString());
            assertThat(paymentService.processedChargeCount()).isEqualTo(before + 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/process-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"FRW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("Idempotency-Key header is required."));
    }
}
