sequenceDiagram
    actor I as Interviewer
    participant API as VietRecruit API

    I->>API: POST /auth/login
    API-->>I: accessToken

    I->>API: GET /interviews/{id}
    API-->>I: InterviewResponse (title, scheduledAt, locationOrLink, candidates)

    Note over I: Tiến hành phỏng vấn offline / Zoom / Teams

    I->>API: POST /interviews/{id}/scorecards
    Note right of I: skillScore, attitudeScore,<br/>englishScore, result,<br/>comments
    API-->>I: ScorecardResponse (averageScore tự tính bởi PostgreSQL)