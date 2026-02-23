package com.vietrecruit.common;

public final class ApiConstants {
    private ApiConstants() {}

    public static final String API_VERSION = "/vietrecruit";

    public static final class Auth {
        private Auth() {}

        public static final String ROOT = API_VERSION + "/auth";
        public static final String LOGIN = "/login";
        public static final String REGISTER = "/register";
        public static final String REFRESH = "/refresh";
        public static final String LOGOUT = "/logout";
        public static final String FORGOT_PASSWORD = "/forgot-password";
    }

    public static final class User {
        private User() {}

        public static final String ROOT = API_VERSION + "/users";
        public static final String SEARCH = "/search";
        public static final String GET = "/{id}";
        public static final String CREATE = "/create";
        public static final String UPDATE = "/{id}";
        public static final String DELETE = "/{id}";
    }
}
