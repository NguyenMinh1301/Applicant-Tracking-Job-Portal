package com.vietrecruit.common.response;

import lombok.Getter;

@Getter
public enum ApiSuccessCode {
    // spotless:off

    // Auth
    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "Registration completed successfully"),
    AUTH_LOGIN_SUCCESS("AUTH_LOGIN_SUCCESS", "Authentication successful"),
    AUTH_REFRESH_SUCCESS("AUTH_REFRESH_SUCCESS", "Token refresh successful"),
    AUTH_LOGOUT_SUCCESS("AUTH_LOGOUT_SUCCESS", "Logout completed successfully"),
    AUTH_INTROSPECT_SUCCESS("AUTH_INTROSPECT_SUCCESS", "Token introspection completed"),
    AUTH_VERIFY_SUCCESS("AUTH_VERIFY_SUCCESS", "Email verification successful"),
    AUTH_VERIFY_RESENT("AUTH_VERIFY_RESENT",
            "If the email exists and is unverified, a new verification code has been sent"),
    AUTH_OAUTH2_SUCCESS("AUTH_OAUTH2_SUCCESS", "OAuth2 authentication successful"),
    AUTH_FORGOT_SUCCESS("AUTH_FORGOT_SUCCESS", "If the email exists, reset instructions have been sent"),
    AUTH_RESET_SUCCESS("AUTH_RESET_SUCCESS", "Password reset successful"),
    AUTH_RESET_TOKEN_VALID("AUTH_RESET_TOKEN_VALID", "Reset token validated, please provide a new password"),
    AUTH_ME_SUCCESS("AUTH_ME_SUCCESS", "Current user profile retrieved successfully"),

    // User
    USER_CREATE_SUCCESS("USER_CREATE_SUCCESS", "User created successfully"),
    USER_UPDATE_SUCCESS("USER_UPDATE_SUCCESS", "User updated successfully"),
    USER_DELETE_SUCCESS("USER_DELETE_SUCCESS", "User deleted successfully"),
    USER_FETCH_SUCCESS("USER_FETCH_SUCCESS", "User retrieved successfully"),
    USER_LIST_SUCCESS("USER_LIST_SUCCESS", "User list retrieved successfully"),
    USER_SEARCH_SUCCESS("USER_SEARCH_SUCCESS", "User search completed successfully"),

    // Subscription
    PLAN_LIST_SUCCESS("PLAN_LIST_SUCCESS", "Plans retrieved successfully"),
    PLAN_FETCH_SUCCESS("PLAN_FETCH_SUCCESS", "Plan retrieved successfully"),
    SUBSCRIPTION_CREATE_SUCCESS("SUBSCRIPTION_CREATE_SUCCESS", "Subscription activated successfully"),
    SUBSCRIPTION_FETCH_SUCCESS("SUBSCRIPTION_FETCH_SUCCESS", "Subscription retrieved successfully"),
    SUBSCRIPTION_CANCEL_SUCCESS("SUBSCRIPTION_CANCEL_SUCCESS", "Subscription cancelled successfully"),
    QUOTA_FETCH_SUCCESS("QUOTA_FETCH_SUCCESS", "Quota usage retrieved successfully");

    // spotless:on
    private final String code;
    private final String defaultMessage;

    ApiSuccessCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
