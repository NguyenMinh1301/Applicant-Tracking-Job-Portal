package com.vietrecruit.common.exception;

import com.vietrecruit.common.enums.ApiErrorCode;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String message) {
        super(message != null && !message.isBlank() ? message : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(
                message != null && !message.isBlank() ? message : errorCode.getDefaultMessage(),
                cause);
        this.errorCode = errorCode;
    }
}
