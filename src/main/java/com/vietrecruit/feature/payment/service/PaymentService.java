package com.vietrecruit.feature.payment.service;

import java.util.UUID;

import com.vietrecruit.common.enums.BillingCycle;
import com.vietrecruit.feature.payment.dto.response.CheckoutResponse;
import com.vietrecruit.feature.payment.dto.response.PaymentStatusResponse;

public interface PaymentService {

    /**
     * Creates a PayOS payment order for the given plan and billing cycle, returning a checkout URL.
     *
     * @param companyId the purchasing company's UUID
     * @param planId the subscription plan's UUID
     * @param cycle the chosen billing cycle (MONTHLY or YEARLY)
     * @return checkout response including the PayOS payment URL
     */
    CheckoutResponse initiateCheckout(UUID companyId, UUID planId, BillingCycle cycle);

    /**
     * Processes an incoming PayOS webhook payload, verifying the signature and recording the
     * payment result.
     *
     * @param webhookBody the raw webhook request body
     */
    void handleWebhook(Object webhookBody);

    /**
     * Activates the subscription associated with a successfully paid order; called after webhook
     * confirmation or reconciliation.
     *
     * @param orderCode the PayOS order code identifying the payment transaction
     */
    void activateAfterPayment(Long orderCode);

    /**
     * Returns the current payment status for the given order, scoped to the requesting company.
     *
     * @param orderCode the PayOS order code identifying the payment transaction
     * @param companyId the owning company's UUID
     * @return payment status response
     */
    PaymentStatusResponse getPaymentStatus(Long orderCode, UUID companyId);
}
