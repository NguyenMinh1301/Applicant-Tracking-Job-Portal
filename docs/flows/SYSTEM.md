flowchart TD
    subgraph AUTH["Xác Thực"]
        R[POST /auth/register] --> OTP[POST /auth/verify-otp]
        OTP --> LOGIN[POST /auth/login]
        LOGIN --> TOKEN["accessToken + refreshToken"]
    end

    subgraph EMPLOYER["Employer / COMPANY_ADMIN"]
        E1[PUT /companies/me] --> E2[POST /departments]
        E2 --> E3[POST /locations]
        E3 --> E4[GET /plans]
        E4 --> E5[POST /payment/checkout]
        E5 --> E6["PayOS ✓"]
        E6 --> E7[GET /subscriptions/current]
    end

    subgraph HR["HR"]
        H1[POST /jobs] --> H2[PUT /jobs/id/publish]
        H2 --> H3[GET /applications]
        H3 --> H4[PUT /applications/id/status]
        H4 --> H5[POST /applications/id/interviews]
        H5 --> H6[PUT /interviews/id/status]
        H6 --> H7[GET /interviews/id/scorecards]
        H7 --> H8[PUT /applications/id/status → OFFER]
        H8 --> H9[POST /applications/id/offers]
        H9 --> H10[PUT /offers/id/send]
    end

    subgraph CANDIDATE["Candidate"]
        C1[PUT /candidates/me] --> C2[POST /candidates/me/cv]
        C2 --> C3[GET /jobs/public]
        C3 --> C4[POST /applications]
        C4 --> C5[GET /applications/mine]
        C5 --> C6[GET /interviews/id]
        C6 --> C7[GET /offers/id]
        C7 --> C8[PUT /offers/id/respond]
    end

    subgraph INTERVIEWER["Interviewer"]
        I1[GET /interviews/id] --> I2[POST /interviews/id/scorecards]
    end

    TOKEN --> EMPLOYER
    TOKEN --> HR
    TOKEN --> CANDIDATE
    TOKEN --> INTERVIEWER
    H2 -->|"Job PUBLISHED"| C3
    H5 -->|"Interview assigned"| I1
    H10 -->|"Offer SENT"| C7
    C8 -->|"ACCEPTED → HIRED"| H3