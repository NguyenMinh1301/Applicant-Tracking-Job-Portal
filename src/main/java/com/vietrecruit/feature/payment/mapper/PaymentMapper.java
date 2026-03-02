package com.vietrecruit.feature.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.vietrecruit.feature.payment.dto.response.CheckoutResponse;
import com.vietrecruit.feature.payment.dto.response.PaymentStatusResponse;
import com.vietrecruit.feature.payment.entity.PaymentTransaction;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "checkoutUrl", source = "checkoutUrl")
    @Mapping(target = "orderCode", source = "orderCode")
    CheckoutResponse toCheckoutResponse(PaymentTransaction transaction);

    @Mapping(target = "orderCode", source = "orderCode")
    @Mapping(target = "status", expression = "java(transaction.getStatus().name())")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "createdAt", source = "createdAt")
    PaymentStatusResponse toPaymentStatusResponse(PaymentTransaction transaction);
}
