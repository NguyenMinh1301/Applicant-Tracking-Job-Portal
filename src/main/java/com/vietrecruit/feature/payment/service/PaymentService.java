package com.vietrecruit.feature.payment.service;

import java.util.UUID;

import com.vietrecruit.common.enums.BillingCycle;
import com.vietrecruit.feature.payment.dto.response.CheckoutResponse;
import com.vietrecruit.feature.payment.dto.response.PaymentStatusResponse;

public interface PaymentService {

    CheckoutResponse initiateCheckout(UUID companyId, UUID planId, BillingCycle cycle);

    void handleWebhook(Object webhookBody);

    void activateAfterPayment(Long orderCode);

    PaymentStatusResponse getPaymentStatus(Long orderCode, UUID companyId);
}
