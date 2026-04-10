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
        public static final String OAUTH2_EXCHANGE = "/oauth2/exchange";
        public static final String CHANGE_PASSWORD = "/change-password";
        public static final String RESET_PASSWORD = "/reset-password";
        public static final String REGISTER_INVITE = "/register/invite";
    }

    public static final class ClientUser {
        private ClientUser() {}

        public static final String ROOT = API_VERSION + "/users";
        public static final String ME = "/me";
        public static final String ME_AVATAR = "/me/avatar";
        public static final String ME_AVATAR_URL = "/me/avatar/url";
        public static final String ME_BANNER = "/me/banner";
        public static final String ME_BANNER_URL = "/me/banner/url";
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

    public static final class Invitation {
        private Invitation() {}

        public static final String ROOT = API_VERSION + "/invitations";
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

    public static final class Job {
        private Job() {}

        public static final String ROOT = API_VERSION + "/jobs";
        public static final String GET = "/{id}";
        public static final String UPDATE = "/{id}";
        public static final String PUBLISH = "/{id}/publish";
        public static final String CLOSE = "/{id}/close";
        public static final String SALARY_BENCHMARK = "/{id}/salary-benchmark";
        public static final String PUBLIC_ROOT = "/public";
        public static final String PUBLIC_GET = "/public/{id}";
        public static final String SEARCH = "/search";
        public static final String AUTOCOMPLETE = "/autocomplete";
        public static final String AI_GENERATE_DESCRIPTION = "/ai/generate-description";
        public static final String AI_APPLY_DESCRIPTION = "/{id}/ai/apply-description";
    }

    public static final class Company {
        private Company() {}

        public static final String ROOT = API_VERSION + "/companies";
        public static final String CREATE = "";
        public static final String ME = "/me";
        public static final String ME_MEMBERS = "/me/members";
        public static final String SEARCH = "/search";
    }

    public static final class Department {
        private Department() {}

        public static final String ROOT = API_VERSION + "/departments";
        public static final String GET = "/{id}";
        public static final String UPDATE = "/{id}";
        public static final String DELETE = "/{id}";
    }

    public static final class Location {
        private Location() {}

        public static final String ROOT = API_VERSION + "/locations";
        public static final String GET = "/{id}";
        public static final String UPDATE = "/{id}";
        public static final String DELETE = "/{id}";
    }

    public static final class Category {
        private Category() {}

        public static final String ROOT = API_VERSION + "/categories";
        public static final String GET = "/{id}";
        public static final String UPDATE = "/{id}";
        public static final String DELETE = "/{id}";
    }

    public static final class Candidate {
        private Candidate() {}

        public static final String ROOT = API_VERSION + "/candidates";
        public static final String ME = "/me";
        public static final String ME_CV = "/me/cv";
        public static final String ME_JOB_RECOMMENDATIONS = "/me/job-recommendations";
        public static final String ME_CV_IMPROVEMENT = "/me/cv/improvement";
        public static final String ME_SALARY_BENCHMARK = "/me/salary-benchmark";
        public static final String GET = "/{id}";
        public static final String SEARCH = "/search";
    }

    public static final class Application {
        private Application() {}

        public static final String ROOT = API_VERSION + "/applications";
        public static final String GET = "/{id}";
        public static final String MINE = "/mine";
        public static final String STATUS = "/{id}/status";
        public static final String STATUS_HISTORY = "/{id}/status-history";
        public static final String INTERVIEWS = "/{id}/interviews";
        public static final String OFFERS = "/{id}/offers";
        public static final String SCREENING = "/jobs/{jobId}/screening";
        public static final String SCREENING_TRIGGER = "/jobs/{jobId}/screening/trigger";
    }

    public static final class Interview {
        private Interview() {}

        public static final String ROOT = API_VERSION + "/interviews";
        public static final String GET = "/{id}";
        public static final String STATUS = "/{id}/status";
        public static final String SCORECARDS = "/{id}/scorecards";
        public static final String QUESTIONS = "/{id}/questions";
        public static final String QUESTIONS_GENERATE = "/{id}/questions/generate";
    }

    public static final class Offer {
        private Offer() {}

        public static final String ROOT = API_VERSION + "/offers";
        public static final String GET = "/{id}";
        public static final String SEND = "/{id}/send";
        public static final String RESPOND = "/{id}/respond";
    }
}
