package com.vietrecruit.common.exception;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.payment.exception.WebhookVerificationException;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(ApiResponse.failure(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getMessage();
        if (ex.getBindingResult().hasErrors()) {
            message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        return buildErrorResponse(ApiErrorCode.VALIDATION_ERROR, message, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        violation ->
                                errors.put(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()));
        return buildErrorResponse(ApiErrorCode.VALIDATION_ERROR, ex.getMessage(), errors);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitException(RequestNotPermitted ex) {
        return buildErrorResponse(
                ApiErrorCode.TOO_MANY_REQUESTS,
                ApiErrorCode.TOO_MANY_REQUESTS.getDefaultMessage(),
                null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex) {
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler({UsernameNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex) {
        return buildErrorResponse(ApiErrorCode.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return buildErrorResponse(
                ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                ApiErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage(),
                null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(ApiErrorCode.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {
        log.warn("File upload rejected: size exceeds limit. {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.FILE_TOO_LARGE, ApiErrorCode.FILE_TOO_LARGE.getDefaultMessage(), null);
    }

    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebhookVerification(
            WebhookVerificationException ex) {
        log.warn("Webhook verification failed: {}", ex.getMessage());
        return buildErrorResponse(ApiErrorCode.UNAUTHORIZED, "Webhook verification failed", null);
    }

    @ExceptionHandler({
        org.springframework.ai.retry.NonTransientAiException.class,
        org.springframework.ai.retry.TransientAiException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAiException(RuntimeException ex) {
        log.error("AI service error: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.AI_SERVICE_UNAVAILABLE,
                ApiErrorCode.AI_SERVICE_UNAVAILABLE.getDefaultMessage(),
                null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception ex) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(
                ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(
            ApiErrorCode code, String message, T data) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.failure(code, message, data));
    }
}
