package com.vietrecruit.feature.payment.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.feature.payment.dto.response.TransactionHistoryResponse;

public interface TransactionHistoryService {

    Page<TransactionHistoryResponse> getCompanyTransactions(UUID companyId, Pageable pageable);

    Page<TransactionHistoryResponse> getAllTransactions(Pageable pageable);
}
