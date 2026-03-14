package com.vietrecruit.feature.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.security.oauth2.OAuth2AuthorizationCodeStore;
import com.vietrecruit.feature.auth.dto.request.OAuth2CodeExchangeRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Auth.ROOT)
@Tag(name = "Auth Service", description = "OAuth2 code exchange endpoint")
public class OAuth2ExchangeController extends BaseController {

    private final OAuth2AuthorizationCodeStore authorizationCodeStore;

    @Operation(
            summary = "Exchange OAuth2 Code",
            description = "Exchanges a one-time authorization code for JWT tokens")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Tokens returned successfully")
    @PostMapping(ApiConstants.Auth.OAUTH2_EXCHANGE)
    public ResponseEntity<ApiResponse<LoginResponse>> exchangeCode(
            @Valid @RequestBody OAuth2CodeExchangeRequest request) {

        LoginResponse tokens =
                authorizationCodeStore
                        .exchangeCode(request.getCode())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_OAUTH2_CODE_INVALID));

        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_LOGIN_SUCCESS, tokens));
    }
}
