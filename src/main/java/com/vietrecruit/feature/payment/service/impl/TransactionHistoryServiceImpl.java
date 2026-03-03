package com.vietrecruit.feature.payment.service.impl;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.feature.payment.dto.response.TransactionHistoryResponse;
import com.vietrecruit.feature.payment.mapper.PaymentMapper;
import com.vietrecruit.feature.payment.repository.TransactionRecordRepository;
import com.vietrecruit.feature.payment.service.TransactionHistoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryServiceImpl implements TransactionHistoryService {

    private final TransactionRecordRepository transactionRecordRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public Page<TransactionHistoryResponse> getCompanyTransactions(
            UUID companyId, Pageable pageable) {
        return transactionRecordRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                .map(paymentMapper::toTransactionHistoryResponse);
    }

    @Override
    public Page<TransactionHistoryResponse> getAllTransactions(Pageable pageable) {
        return transactionRecordRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(paymentMapper::toTransactionHistoryResponse);
    }
}
