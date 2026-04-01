package com.vietrecruit.feature.payment.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.feature.payment.dto.response.TransactionHistoryResponse;

public interface TransactionHistoryService {

    /**
     * Returns a paginated transaction history for the given company.
     *
     * @param companyId the owning company's UUID
     * @param pageable pagination and sort parameters
     * @return page of transaction history entries
     */
    Page<TransactionHistoryResponse> getCompanyTransactions(UUID companyId, Pageable pageable);

    /**
     * Returns a paginated transaction history across all companies, for admin use.
     *
     * @param pageable pagination and sort parameters
     * @return page of transaction history entries
     */
    Page<TransactionHistoryResponse> getAllTransactions(Pageable pageable);
}
