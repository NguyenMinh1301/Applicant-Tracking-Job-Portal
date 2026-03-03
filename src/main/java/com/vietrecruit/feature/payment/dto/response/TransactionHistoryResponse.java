package com.vietrecruit.feature.payment.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryResponse {

    private String counterAccountName;
    private Instant transactionDateTime;
    private String description;
    private Long amount;
    private String currency;
    private String status;
    private Long orderCode;
}
