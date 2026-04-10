package com.vietrecruit.common.exception;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        log.warn("Validation failed: {}", ex.getMessage());
        String message = ex.getMessage();
        if (ex.getBindingResult().hasErrors()) {
            message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        return buildErrorResponse(ApiErrorCode.VALIDATION_ERROR, message, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        violation ->
                                errors.put(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()));
        String message =
                ex.getConstraintViolations().isEmpty()
                        ? "Validation constraint violated"
                        : ex.getConstraintViolations().iterator().next().getMessage();
        return buildErrorResponse(ApiErrorCode.VALIDATION_ERROR, message, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        String message = "Invalid request body format";
        Throwable cause = ex.getCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null) {
                if (causeMessage.contains("Instant")) {
                    message =
                            "Invalid date/time format. Expected ISO-8601 format (e.g., "
                                    + "2026-04-10T10:00:00Z)";
                } else if (causeMessage.contains("JSON")) {
                    message = "Malformed JSON in request body";
                } else if (causeMessage.contains("Required request body is missing")) {
                    message = "Request body is required";
                }
            }
        }
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getValue());
        String message =
                String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, message, null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(Exception ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.NOT_FOUND, "The requested resource was not found", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMethod());
        String message =
                String.format(
                        "HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String message = "A data constraint was violated";
        String rootMessage = ex.getMostSpecificCause().getMessage();
        if (rootMessage != null) {
            if (rootMessage.contains("unique constraint")
                    || rootMessage.contains("duplicate key")) {
                if (rootMessage.toLowerCase().contains("email")) {
                    message = "Email already exists";
                } else if (rootMessage.toLowerCase().contains("username")) {
                    message = "Username already exists";
                } else {
                    message = "A record with this value already exists";
                }
            } else if (rootMessage.contains("foreign key constraint")) {
                message = "Cannot complete operation due to related records";
            } else if (rootMessage.contains("not-null constraint")) {
                message = "Required field is missing";
            }
        }
        return buildErrorResponse(ApiErrorCode.CONFLICT, message, null);
    }

    @ExceptionHandler({
        OptimisticLockingFailureException.class,
        ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(Exception ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.CONCURRENT_MODIFICATION,
                ApiErrorCode.CONCURRENT_MODIFICATION.getDefaultMessage(),
                null);
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmptyResult(EmptyResultDataAccessException ex) {
        log.warn("Entity not found for delete operation: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.NOT_FOUND, "The requested resource was not found", null);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionSystemException(
            TransactionSystemException ex) {
        log.warn("Transaction system exception: {}", ex.getMessage());
        Throwable cause = ex.getRootCause();
        if (cause instanceof ConstraintViolationException) {
            ConstraintViolationException cve = (ConstraintViolationException) cause;
            log.warn("Constraint violation in transaction: {}", cve.getMessage());
            String message =
                    cve.getConstraintViolations().isEmpty()
                            ? "Validation constraint violated"
                            : cve.getConstraintViolations().iterator().next().getMessage();
            return buildErrorResponse(ApiErrorCode.VALIDATION_ERROR, message, null);
        }
        return buildErrorResponse(
                ApiErrorCode.BAD_REQUEST, "Transaction failed due to validation error", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED.getDefaultMessage(), null);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitException(RequestNotPermitted ex) {
        log.warn("Rate limit exceeded");
        return buildErrorResponse(
                ApiErrorCode.TOO_MANY_REQUESTS,
                ApiErrorCode.TOO_MANY_REQUESTS.getDefaultMessage(),
                null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Invalid request parameter";
        }
        return buildErrorResponse(ApiErrorCode.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Operation cannot be performed in current state";
        }
        return buildErrorResponse(ApiErrorCode.CONFLICT, message, null);
    }

    @ExceptionHandler({UsernameNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "The requested resource was not found";
        }
        return buildErrorResponse(ApiErrorCode.NOT_FOUND, message, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return buildErrorResponse(
                ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                ApiErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage(),
                null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(
                ApiErrorCode.FORBIDDEN, ApiErrorCode.FORBIDDEN.getDefaultMessage(), null);
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
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(
            ApiErrorCode code, String message, T data) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.failure(code, message, data));
    }
}
