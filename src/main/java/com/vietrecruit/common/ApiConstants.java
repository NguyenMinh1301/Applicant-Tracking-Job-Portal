package com.vietrecruit.common;

public final class ApiConstants {
    private ApiConstants() {}

    public static final String API_VERSION = "/vietrecruit";
    public static final String ADMIN_PREFIX = API_VERSION + "/admin";

    public static final class Auth {
        private Auth() {}

        public static final String ROOT = API_VERSION + "/auth";
        public static final String LOGIN = "/login";
        public static final String REGISTER = "/register";
        public static final String REFRESH = "/refresh";
        public static final String LOGOUT = "/logout";
        public static final String FORGOT_PASSWORD = "/forgot-password";
        public static final String VERIFY_OTP = "/verify-otp";
        public static final String RESEND_OTP = "/resend-otp";
        public static final String OAUTH2_CALLBACK = "/oauth2/callback/*";
        public static final String CHANGE_PASSWORD = "/change-password";
        public static final String RESET_PASSWORD = "/reset-password";
    }

    public static final class ClientUser {
        private ClientUser() {}

        public static final String ROOT = API_VERSION + "/users";
        public static final String ME = "/me";
    }

    public static final class AdminUser {
        private AdminUser() {}

        public static final String ROOT = ADMIN_PREFIX + "/users";
        public static final String GET = "/{id}";
        public static final String CREATE = "";
        public static final String UPDATE = "/{id}";
        public static final String DELETE = "/{id}";
        public static final String SEARCH = "/search";
    }

    public static final class Plan {
        private Plan() {}

        public static final String ROOT = API_VERSION + "/plans";
        public static final String GET = "/{planId}";
    }

    public static final class Subscription {
        private Subscription() {}

        public static final String ROOT = API_VERSION + "/subscriptions";
        public static final String CURRENT = "/current";
        public static final String CANCEL = "/current/cancel";
        public static final String QUOTA = "/current/quota";
    }

    public static final class Payment {
        private Payment() {}

        public static final String ROOT = API_VERSION + "/payment";
        public static final String CHECKOUT = "/checkout";
        public static final String PAYMENT_STATUS = "/payment-status/{orderCode}";
        public static final String TRANSACTIONS = "/transactions";
    }

    public static final class AdminPayment {
        private AdminPayment() {}

        public static final String ROOT = ADMIN_PREFIX + "/payment";
        public static final String TRANSACTIONS = "/transactions";
    }

    public static final class Webhook {
        private Webhook() {}

        public static final String ROOT = API_VERSION + "/webhooks";
        public static final String PAYOS = "/payos";
    }
}
