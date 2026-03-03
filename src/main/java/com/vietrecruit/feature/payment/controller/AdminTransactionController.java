package com.vietrecruit.feature.payment.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.payment.dto.response.TransactionHistoryResponse;
import com.vietrecruit.feature.payment.service.TransactionHistoryService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.AdminPayment.ROOT)
@Tag(
        name = "Admin Transaction History",
        description = "Admin/Customer Service endpoints for viewing all transaction records")
public class AdminTransactionController {

    private final TransactionHistoryService transactionHistoryService;

    @Operation(
            summary = "Get All Transaction History",
            description =
                    "Retrieves paginated transaction history across all companies. "
                            + "Optionally filter by companyId.")
    @PreAuthorize("hasAuthority('TRANSACTION:VIEW_ALL')")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.AdminPayment.TRANSACTIONS)
    public ResponseEntity<ApiResponse<Page<TransactionHistoryResponse>>> getAllTransactions(
            @RequestParam(required = false) UUID companyId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<TransactionHistoryResponse> result;
        if (companyId != null) {
            result = transactionHistoryService.getCompanyTransactions(companyId, pageable);
        } else {
            result = transactionHistoryService.getAllTransactions(pageable);
        }

        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.TRANSACTION_HISTORY_FETCH_SUCCESS, result));
    }
}
