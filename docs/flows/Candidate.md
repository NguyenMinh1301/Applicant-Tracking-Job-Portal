sequenceDiagram
    actor C as Candidate
    participant API as VietRecruit API
    participant R2 as Cloudflare R2
    participant Email as Email Service

    C->>API: POST /auth/register
    API-->>Email: Gửi OTP
    API-->>C: 200 OK

    C->>API: POST /auth/verify-otp {email, otp}
    API-->>C: 200 Account activated

    C->>API: POST /auth/login {email, password}
    API-->>C: accessToken + refreshToken

    C->>API: PUT /candidates/me {headline, skills, experience...}
    API-->>C: CandidateProfileResponse

    C->>API: POST /candidates/me/cv [file PDF/DOCX]
    API->>R2: upload candidates/{userId}/cv
    R2-->>API: objectKey
    API-->>C: cvUrl

    C->>API: GET /jobs/public?keyword=backend
    API-->>C: Paginated JobSummaryResponse[]

    C->>API: GET /jobs/public/{jobId}
    API-->>C: JobResponse (full detail)

    C->>API: POST /applications {jobId, coverLetter}
    API-->>C: ApplicationResponse (status: NEW)

    Note over C,API: HR xử lý pipeline...

    C->>API: GET /applications/mine
    API-->>C: Paginated applications

    C->>API: GET /interviews/{id}
    API-->>C: Interview details (time, link)

    C->>API: GET /offers/{id}
    API-->>C: OfferResponse (salary, startDate)

    C->>API: PUT /offers/{id}/respond {action: "ACCEPT"}
    API-->>C: OfferResponse (status: ACCEPTED)
    Note over API: Application → HIRED (atomic)