package com.vietrecruit.common.enums;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ApiErrorCode {
    // spotless:off

    // Common
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("BAD_REQUEST", "Invalid request", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "Requested resource was not found", HttpStatus.NOT_FOUND),
    FORBIDDEN("FORBIDDEN", "Access to this resource is forbidden", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "The system is busy. Please try again in a few minutes.",
            HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "External service temporarily unavailable. Please try again later.",
            HttpStatus.SERVICE_UNAVAILABLE),

    // Auth
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Access token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Invalid access token", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_EXPIRED("AUTH_REFRESH_TOKEN_EXPIRED", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_REFRESH_TOKEN_INVALID", "Invalid or revoked refresh token",
            HttpStatus.UNAUTHORIZED),
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "Account is temporarily locked due to too many failed attempts",
            HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_INACTIVE("AUTH_ACCOUNT_INACTIVE", "Account is inactive", HttpStatus.FORBIDDEN),
    AUTH_PASSWORD_MISMATCH("AUTH_PASSWORD_MISMATCH", "Current password is incorrect", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_INVALID("AUTH_RESET_TOKEN_INVALID", "Invalid or expired reset token",
            HttpStatus.BAD_REQUEST),

    // User
    USER_USERNAME_CONFLICT("USER_USERNAME_CONFLICT", "Username already exists", HttpStatus.CONFLICT),
    USER_EMAIL_CONFLICT("USER_EMAIL_CONFLICT", "Email already exists", HttpStatus.CONFLICT),

    // Email Verification
    AUTH_OTP_INVALID("AUTH_OTP_INVALID", "Verification code is incorrect", HttpStatus.BAD_REQUEST),
    AUTH_OTP_EXPIRED("AUTH_OTP_EXPIRED", "Verification code has expired", HttpStatus.BAD_REQUEST),
    AUTH_OTP_COOLDOWN("AUTH_OTP_COOLDOWN", "Please wait before requesting a new code",
            HttpStatus.TOO_MANY_REQUESTS),
    AUTH_OTP_LOCKED("AUTH_OTP_LOCKED", "Too many failed attempts. Please try again later", HttpStatus.FORBIDDEN),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_EMAIL_NOT_VERIFIED", "Email address has not been verified",
            HttpStatus.FORBIDDEN),

    // OAuth2
    AUTH_OAUTH2_FAILED("AUTH_OAUTH2_FAILED", "OAuth2 authentication failed", HttpStatus.UNAUTHORIZED),
    AUTH_OAUTH2_EMAIL_MISSING("AUTH_OAUTH2_EMAIL_MISSING", "Email not available from OAuth2 provider",
            HttpStatus.BAD_REQUEST),
    AUTH_OAUTH2_CODE_INVALID("AUTH_OAUTH2_CODE_INVALID", "Invalid or expired authorization code",
            HttpStatus.BAD_REQUEST),

    // Notification
    NOTIFICATION_SEND_FAILED("NOTIFICATION_SEND_FAILED", "Failed to send notification",
            HttpStatus.INTERNAL_SERVER_ERROR),

    // Subscription
    SUBSCRIPTION_REQUIRED("SUBSCRIPTION_REQUIRED", "An active subscription is required",
            HttpStatus.FORBIDDEN),
    SUBSCRIPTION_EXPIRED("SUBSCRIPTION_EXPIRED", "Your subscription has expired",
            HttpStatus.FORBIDDEN),
    SUBSCRIPTION_ALREADY_ACTIVE("SUBSCRIPTION_ALREADY_ACTIVE",
            "An active subscription already exists", HttpStatus.CONFLICT),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "Active job posting limit reached for your plan",
            HttpStatus.TOO_MANY_REQUESTS),
    PLAN_NOT_FOUND("PLAN_NOT_FOUND", "Subscription plan not found", HttpStatus.NOT_FOUND),

    // Payment
    PAYMENT_CREATION_FAILED("PAYMENT_CREATION_FAILED", "Failed to create payment link",
            HttpStatus.BAD_GATEWAY),
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "Payment transaction not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PENDING("PAYMENT_ALREADY_PENDING", "A pending payment already exists",
            HttpStatus.CONFLICT),
    PAYMENT_EXPIRED("PAYMENT_EXPIRED", "Payment link has expired", HttpStatus.GONE),
    PAYMENT_ACTIVATION_FAILED("PAYMENT_ACTIVATION_FAILED",
            "Subscription activation failed after payment", HttpStatus.INTERNAL_SERVER_ERROR),

    // Candidate
    CANDIDATE_NOT_FOUND("CANDIDATE_NOT_FOUND", "Candidate profile not found", HttpStatus.NOT_FOUND),
    CANDIDATE_CV_INVALID_TYPE("CANDIDATE_CV_INVALID_TYPE",
            "Unsupported file type. Accepted: PDF, DOCX, JPEG, PNG",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CANDIDATE_CV_SIZE_EXCEEDED("CANDIDATE_CV_SIZE_EXCEEDED",
            "File size exceeds the 5MB limit", HttpStatus.BAD_REQUEST),

    // Storage
    STORAGE_UNAVAILABLE("STORAGE_UNAVAILABLE",
            "File storage service is temporarily unavailable. Please try again later.",
            HttpStatus.SERVICE_UNAVAILABLE),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "File size exceeds the allowed limit",
            HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED",
            "File type is not allowed. Content does not match expected format.",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // User Avatar & Banner
    USER_AVATAR_INVALID_TYPE("USER_AVATAR_INVALID_TYPE",
            "Unsupported image type. Accepted: JPEG, PNG, WebP",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    USER_AVATAR_SIZE_EXCEEDED("USER_AVATAR_SIZE_EXCEEDED",
            "Avatar file size exceeds the 2MB limit", HttpStatus.BAD_REQUEST),
    USER_BANNER_INVALID_TYPE("USER_BANNER_INVALID_TYPE",
            "Unsupported image type. Accepted: JPEG, PNG, WebP",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    USER_BANNER_SIZE_EXCEEDED("USER_BANNER_SIZE_EXCEEDED",
            "Banner file size exceeds the 3MB limit", HttpStatus.BAD_REQUEST),

    // Application
    APPLICATION_NOT_FOUND("APPLICATION_NOT_FOUND", "Application not found", HttpStatus.NOT_FOUND),
    APPLICATION_DUPLICATE("APPLICATION_DUPLICATE", "You have already applied to this job", HttpStatus.CONFLICT),
    APPLICATION_INVALID_TRANSITION("APPLICATION_INVALID_TRANSITION", "Invalid application status transition",
            HttpStatus.BAD_REQUEST),
    APPLICATION_CV_REQUIRED("APPLICATION_CV_REQUIRED", "A CV is required to apply", HttpStatus.BAD_REQUEST),
    JOB_NOT_PUBLISHED("JOB_NOT_PUBLISHED", "Job is not published or does not exist", HttpStatus.NOT_FOUND),

    // Interview
    INTERVIEW_NOT_FOUND("INTERVIEW_NOT_FOUND", "Interview not found", HttpStatus.NOT_FOUND),
    INTERVIEW_INVALID_STATUS("INTERVIEW_INVALID_STATUS",
            "Application must be in INTERVIEW status to schedule interviews", HttpStatus.BAD_REQUEST),
    INTERVIEW_INVALID_INTERVIEWER("INTERVIEW_INVALID_INTERVIEWER",
            "One or more interviewers are not eligible", HttpStatus.BAD_REQUEST),
    INTERVIEW_INVALID_STATUS_TRANSITION("INTERVIEW_INVALID_STATUS_TRANSITION",
            "Invalid interview status transition", HttpStatus.BAD_REQUEST),

    // Scorecard
    SCORECARD_NOT_FOUND("SCORECARD_NOT_FOUND", "Scorecard not found", HttpStatus.NOT_FOUND),
    SCORECARD_DUPLICATE("SCORECARD_DUPLICATE", "Scorecard already submitted for this interview",
            HttpStatus.CONFLICT),
    SCORECARD_NOT_ELIGIBLE("SCORECARD_NOT_ELIGIBLE", "You are not assigned to this interview",
            HttpStatus.FORBIDDEN),
    SCORECARD_INTERVIEW_NOT_READY("SCORECARD_INTERVIEW_NOT_READY",
            "Interview is not in a valid state for scorecard submission", HttpStatus.BAD_REQUEST),

    // Offer
    OFFER_NOT_FOUND("OFFER_NOT_FOUND", "Offer not found", HttpStatus.NOT_FOUND),
    OFFER_ALREADY_EXISTS("OFFER_ALREADY_EXISTS", "An active offer already exists for this application",
            HttpStatus.CONFLICT),
    OFFER_INVALID_TRANSITION("OFFER_INVALID_TRANSITION", "Invalid offer status transition", HttpStatus.BAD_REQUEST),
    OFFER_APPLICATION_NOT_READY("OFFER_APPLICATION_NOT_READY", "Application must be in OFFER status",
            HttpStatus.BAD_REQUEST),

    // Invitation
    INVITATION_NOT_FOUND("INVITATION_NOT_FOUND", "Invitation not found", HttpStatus.NOT_FOUND),
    INVITATION_EXPIRED("INVITATION_EXPIRED", "Invitation has expired", HttpStatus.BAD_REQUEST),
    INVITATION_ALREADY_ACCEPTED("INVITATION_ALREADY_ACCEPTED", "Invitation has already been accepted",
            HttpStatus.CONFLICT),
    INVALID_ACCOUNT_TYPE("INVALID_ACCOUNT_TYPE", "Account type must be CANDIDATE or EMPLOYER",
            HttpStatus.BAD_REQUEST),
    ROLE_GROUP_VIOLATION("ROLE_GROUP_VIOLATION",
            "Roles from different groups cannot be assigned to the same user", HttpStatus.BAD_REQUEST),
    INVALID_INVITATION_ROLE("INVALID_INVITATION_ROLE", "Only HR or INTERVIEWER roles can be invited",
            HttpStatus.BAD_REQUEST),

    // AI
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "AI service is temporarily unavailable",
            HttpStatus.SERVICE_UNAVAILABLE),
    AI_INVALID_RESPONSE("AI_INVALID_RESPONSE", "AI service returned an invalid response",
            HttpStatus.BAD_GATEWAY),

    // Generic
    CONFLICT("CONFLICT", "Resource already exists", HttpStatus.CONFLICT);

    // spotless:on
    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ApiErrorCode(String code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
