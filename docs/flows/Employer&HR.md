sequenceDiagram
    actor E as Employer (COMPANY_ADMIN)
    actor H as HR
    participant API as VietRecruit API
    participant PayOS as PayOS

    E->>API: POST /auth/register {role: COMPANY_ADMIN}
    E->>API: POST /auth/login
    API-->>E: accessToken

    E->>API: PUT /companies/me {name, industry, website}
    API-->>E: CompanyResponse

    E->>API: POST /departments {name}
    API-->>E: DepartmentResponse (id)

    E->>API: POST /locations {name, address}
    API-->>E: LocationResponse (id)

    E->>API: GET /plans
    API-->>E: SubscriptionPlan[]

    E->>API: POST /payment/checkout {planId}
    API->>PayOS: Tạo checkout link
    PayOS-->>API: checkoutUrl
    API-->>E: checkoutUrl + orderCode

    E->>PayOS: Thanh toán
    PayOS->>API: POST /webhooks/payos (signature verified)
    Note over API: Kích hoạt subscription + quota

    H->>API: POST /jobs {title, departmentId, locationId}
    API-->>H: JobResponse (status: DRAFT)

    H->>API: PUT /jobs/{id}/publish
    Note over API: Kiểm tra quota → trừ jobs_active
    API-->>H: JobResponse (status: PUBLISHED)

    H->>API: GET /applications?jobId=...&status=NEW
    API-->>H: Paginated ApplicationSummaryResponse[]

    H->>API: GET /applications/{id}
    API-->>H: Full ApplicationResponse + cvUrl

    H->>API: PUT /applications/{id}/status {status: SCREENING}
    Note over API: Insert application_status_history
    API-->>H: ApplicationResponse

    H->>API: PUT /applications/{id}/status {status: INTERVIEW}
    API-->>H: ApplicationResponse

    H->>API: POST /applications/{id}/interviews {title, scheduledAt, interviewerIds[]}
    API-->>H: InterviewResponse

    H->>API: PUT /interviews/{id}/status {status: COMPLETED}
    API-->>H: InterviewResponse

    H->>API: GET /interviews/{id}/scorecards
    API-->>H: ScorecardResponse[] (scores, averages, results)

    H->>API: PUT /applications/{id}/status {status: OFFER}
    API-->>H: ApplicationResponse

    H->>API: POST /applications/{id}/offers {baseSalary, startDate}
    API-->>H: OfferResponse (status: DRAFT)

    H->>API: PUT /offers/{id}/send
    API-->>H: OfferResponse (status: SENT)

    Note over H,API: Candidate responds (Flow 2)...

    H->>API: GET /applications/{id}/status-history
    API-->>H: StatusHistory[] (HIRED/REJECTED visible)