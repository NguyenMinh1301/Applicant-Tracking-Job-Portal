package com.vietrecruit.feature.payment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.vietrecruit.feature.subscription.entity.BillingCycle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;
}
