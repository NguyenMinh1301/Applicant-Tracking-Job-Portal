# API Error Handling

## Overview

All exceptions are intercepted globally and standardized into a predictable output format to decouple internal errors from the external HTTP contract.

## Flow

- **Interception:** `GlobalExceptionHandler` caches native RuntimeExceptions (e.g., `EntityNotFoundException`) and internal `ApiException` calls.
- **Transformation:** Errors are cast into `ApiResponse<Void>` or `ApiResponse<Map<String, String>>`.
- **Delivery:** HTTP status codes correctly reflect the nature of the error.

## Extracted Response Format

Clients receive the `ApiResponse` entity directly, guaranteeing `success: false` on exception hits.

```java
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    @Builder.Default private Instant timestamp = Instant.now();
    // ...
}
```

## Key Code

The `GlobalExceptionHandler` enforces specific mappings for framework classes and custom validation problems.

```java
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

private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(
        ApiErrorCode code, String message, T data) {
    return ResponseEntity.status(code.getStatus())
            .body(ApiResponse.failure(code, message, data));
}
```
