package com.vietrecruit.common.enums;

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
    AUTH_CHANGE_PASSWORD_SUCCESS("AUTH_CHANGE_PASSWORD_SUCCESS", "Password changed successfully"),
    AUTH_INVITE_REGISTER_SUCCESS("AUTH_INVITE_REGISTER_SUCCESS",
            "Registration via invitation completed successfully"),

    // Invitation
    INVITATION_CREATE_SUCCESS("INVITATION_CREATE_SUCCESS", "Invitation sent successfully"),

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
    QUOTA_FETCH_SUCCESS("QUOTA_FETCH_SUCCESS", "Quota usage retrieved successfully"),

    // Payment
    CHECKOUT_SUCCESS("CHECKOUT_SUCCESS", "Payment link created successfully"),
    PAYMENT_STATUS_FETCH_SUCCESS("PAYMENT_STATUS_FETCH_SUCCESS", "Payment status retrieved successfully"),
    TRANSACTION_HISTORY_FETCH_SUCCESS("TRANSACTION_HISTORY_FETCH_SUCCESS",
            "Transaction history retrieved successfully"),

    // Search
    SEARCH_SUCCESS("SEARCH_SUCCESS", "Search completed successfully"),
    AUTOCOMPLETE_SUCCESS("AUTOCOMPLETE_SUCCESS", "Autocomplete results retrieved successfully"),

    // Job
    JOB_CREATE_SUCCESS("JOB_CREATE_SUCCESS", "Job created successfully"),
    JOB_UPDATE_SUCCESS("JOB_UPDATE_SUCCESS", "Job updated successfully"),
    JOB_PUBLISH_SUCCESS("JOB_PUBLISH_SUCCESS", "Job published successfully"),
    JOB_CLOSE_SUCCESS("JOB_CLOSE_SUCCESS", "Job closed successfully"),
    JOB_FETCH_SUCCESS("JOB_FETCH_SUCCESS", "Job retrieved successfully"),
    JOB_LIST_SUCCESS("JOB_LIST_SUCCESS", "Job list retrieved successfully"),

    // Company
    COMPANY_CREATE_SUCCESS("COMPANY_CREATE_SUCCESS", "Company created successfully"),
    COMPANY_FETCH_SUCCESS("COMPANY_FETCH_SUCCESS", "Company retrieved successfully"),
    COMPANY_UPDATE_SUCCESS("COMPANY_UPDATE_SUCCESS", "Company updated successfully"),

    // Department
    DEPARTMENT_CREATE_SUCCESS("DEPARTMENT_CREATE_SUCCESS", "Department created successfully"),
    DEPARTMENT_UPDATE_SUCCESS("DEPARTMENT_UPDATE_SUCCESS", "Department updated successfully"),
    DEPARTMENT_DELETE_SUCCESS("DEPARTMENT_DELETE_SUCCESS", "Department deleted successfully"),
    DEPARTMENT_FETCH_SUCCESS("DEPARTMENT_FETCH_SUCCESS", "Department retrieved successfully"),
    DEPARTMENT_LIST_SUCCESS("DEPARTMENT_LIST_SUCCESS", "Department list retrieved successfully"),

    // Location
    LOCATION_CREATE_SUCCESS("LOCATION_CREATE_SUCCESS", "Location created successfully"),
    LOCATION_UPDATE_SUCCESS("LOCATION_UPDATE_SUCCESS", "Location updated successfully"),
    LOCATION_DELETE_SUCCESS("LOCATION_DELETE_SUCCESS", "Location deleted successfully"),
    LOCATION_FETCH_SUCCESS("LOCATION_FETCH_SUCCESS", "Location retrieved successfully"),
    LOCATION_LIST_SUCCESS("LOCATION_LIST_SUCCESS", "Location list retrieved successfully"),

    // Category
    CATEGORY_CREATE_SUCCESS("CATEGORY_CREATE_SUCCESS", "Category created successfully"),
    CATEGORY_UPDATE_SUCCESS("CATEGORY_UPDATE_SUCCESS", "Category updated successfully"),
    CATEGORY_DELETE_SUCCESS("CATEGORY_DELETE_SUCCESS", "Category deleted successfully"),
    CATEGORY_FETCH_SUCCESS("CATEGORY_FETCH_SUCCESS", "Category retrieved successfully"),
    CATEGORY_LIST_SUCCESS("CATEGORY_LIST_SUCCESS", "Category list retrieved successfully"),

    // Candidate
    CANDIDATE_FETCH_SUCCESS("CANDIDATE_FETCH_SUCCESS", "Candidate profile retrieved successfully"),
    CANDIDATE_UPDATE_SUCCESS("CANDIDATE_UPDATE_SUCCESS", "Candidate profile updated successfully"),
    CANDIDATE_CV_UPLOAD_SUCCESS("CANDIDATE_CV_UPLOAD_SUCCESS", "CV uploaded successfully"),
    CANDIDATE_CV_DELETE_SUCCESS("CANDIDATE_CV_DELETE_SUCCESS", "CV deleted successfully"),
    CANDIDATE_JOB_RECOMMENDATIONS_SUCCESS("CANDIDATE_JOB_RECOMMENDATIONS_SUCCESS",
            "Job recommendations retrieved successfully"),
    CV_IMPROVEMENT_SUCCESS("CV_IMPROVEMENT_SUCCESS", "CV improvement analysis completed successfully"),

    // Application
    APPLICATION_CREATE_SUCCESS("APPLICATION_CREATE_SUCCESS", "Application submitted successfully"),
    APPLICATION_FETCH_SUCCESS("APPLICATION_FETCH_SUCCESS", "Application retrieved successfully"),
    APPLICATION_LIST_SUCCESS("APPLICATION_LIST_SUCCESS", "Application list retrieved successfully"),
    APPLICATION_STATUS_UPDATE_SUCCESS("APPLICATION_STATUS_UPDATE_SUCCESS",
            "Application status updated successfully"),
    APPLICATION_STATUS_HISTORY_SUCCESS("APPLICATION_STATUS_HISTORY_SUCCESS",
            "Application status history retrieved successfully"),
    APPLICATION_SCREENING_SUCCESS("APPLICATION_SCREENING_SUCCESS",
            "Application screening results retrieved successfully"),
    APPLICATION_SCREENING_TRIGGER_SUCCESS("APPLICATION_SCREENING_TRIGGER_SUCCESS",
            "AI screening triggered successfully"),

    // Interview
    INTERVIEW_CREATE_SUCCESS("INTERVIEW_CREATE_SUCCESS", "Interview scheduled successfully"),
    INTERVIEW_FETCH_SUCCESS("INTERVIEW_FETCH_SUCCESS", "Interview retrieved successfully"),
    INTERVIEW_LIST_SUCCESS("INTERVIEW_LIST_SUCCESS", "Interview list retrieved successfully"),
    INTERVIEW_STATUS_UPDATE_SUCCESS("INTERVIEW_STATUS_UPDATE_SUCCESS",
            "Interview status updated successfully"),
    INTERVIEW_QUESTIONS_GENERATED("INTERVIEW_QUESTIONS_GENERATED",
            "Interview questions generated successfully"),
    INTERVIEW_QUESTIONS_FETCH_SUCCESS("INTERVIEW_QUESTIONS_FETCH_SUCCESS",
            "Interview questions retrieved successfully"),

    // Scorecard
    SCORECARD_CREATE_SUCCESS("SCORECARD_CREATE_SUCCESS", "Scorecard submitted successfully"),
    SCORECARD_LIST_SUCCESS("SCORECARD_LIST_SUCCESS", "Scorecards retrieved successfully"),

    // Offer
    OFFER_CREATE_SUCCESS("OFFER_CREATE_SUCCESS", "Offer created successfully"),
    OFFER_FETCH_SUCCESS("OFFER_FETCH_SUCCESS", "Offer retrieved successfully"),
    OFFER_LIST_SUCCESS("OFFER_LIST_SUCCESS", "Offers retrieved successfully"),
    OFFER_SEND_SUCCESS("OFFER_SEND_SUCCESS", "Offer sent successfully"),
    OFFER_RESPOND_SUCCESS("OFFER_RESPOND_SUCCESS", "Offer response recorded successfully"),
    OFFER_DELETE_SUCCESS("OFFER_DELETE_SUCCESS", "Offer deleted successfully"),

    // Knowledge
    SALARY_BENCHMARK_SUCCESS("SALARY_BENCHMARK_SUCCESS", "Salary benchmark retrieved successfully"),
    JD_GENERATION_SUCCESS("JD_GENERATION_SUCCESS", "Job description generated successfully"),
    JD_APPLY_SUCCESS("JD_APPLY_SUCCESS", "Job description applied successfully"),

    // Knowledge
    KNOWLEDGE_UPLOAD_ACCEPTED("KNOWLEDGE_UPLOAD_ACCEPTED", "Knowledge document upload accepted for processing"),
    KNOWLEDGE_LIST_SUCCESS("KNOWLEDGE_LIST_SUCCESS", "Knowledge documents retrieved successfully"),
    KNOWLEDGE_DELETE_SUCCESS("KNOWLEDGE_DELETE_SUCCESS", "Knowledge document deleted successfully"),

    // User Avatar & Banner
    USER_AVATAR_UPLOAD_SUCCESS("USER_AVATAR_UPLOAD_SUCCESS", "Avatar uploaded successfully"),
    USER_AVATAR_UPDATE_SUCCESS("USER_AVATAR_UPDATE_SUCCESS", "Avatar URL updated successfully"),
    USER_AVATAR_DELETE_SUCCESS("USER_AVATAR_DELETE_SUCCESS", "Avatar deleted successfully"),
    USER_BANNER_UPLOAD_SUCCESS("USER_BANNER_UPLOAD_SUCCESS", "Banner uploaded successfully"),
    USER_BANNER_UPDATE_SUCCESS("USER_BANNER_UPDATE_SUCCESS", "Banner URL updated successfully"),
    USER_BANNER_DELETE_SUCCESS("USER_BANNER_DELETE_SUCCESS", "Banner deleted successfully");

    // spotless:on
    private final String code;
    private final String defaultMessage;

    ApiSuccessCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
