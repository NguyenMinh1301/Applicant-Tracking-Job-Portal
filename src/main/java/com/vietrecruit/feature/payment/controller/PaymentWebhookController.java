package com.vietrecruit.feature.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.feature.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Webhook.ROOT)
@Tag(name = "Payment Webhook", description = "PayOS webhook receiver")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Operation(
            summary = "PayOS Webhook",
            description = "Receives payment status updates from PayOS. Secured by HMAC signature.")
    @PostMapping(ApiConstants.Webhook.PAYOS)
    public ResponseEntity<Void> handlePayOSWebhook(@RequestBody Object body) {
        try {
            paymentService.handleWebhook(body);
        } catch (Exception e) {
            // DB or infrastructure exception — return 500 so PayOS retries
            log.error("Retryable error processing PayOS webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}
