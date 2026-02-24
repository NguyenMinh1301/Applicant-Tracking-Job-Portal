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
        public static final String VERIFY_EMAIL = "/verify-email";
        public static final String RESEND_VERIFICATION = "/resend-verification";
        public static final String OAUTH2_CALLBACK = "/oauth2/callback/*";
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
}
