stateDiagram-v2
    state "Application Status" as APP {
        [*] --> NEW: POST /applications
        NEW --> SCREENING: PUT status (HR)
        NEW --> REJECTED: PUT status (HR)
        SCREENING --> INTERVIEW: PUT status (HR)
        SCREENING --> REJECTED: PUT status (HR)
        INTERVIEW --> OFFER: PUT status (HR)
        INTERVIEW --> REJECTED: PUT status (HR)
        OFFER --> HIRED: PUT /offers/id/respond ACCEPT (Candidate)
        OFFER --> REJECTED: PUT /offers/id/respond DECLINE (Candidate)
        HIRED --> [*]
        REJECTED --> [*]
    }

    state "Interview Status" as INT {
        [*] --> SCHEDULED: POST /interviews
        SCHEDULED --> COMPLETED: PUT /interviews/id/status (HR)
        SCHEDULED --> CANCELED: PUT /interviews/id/status (HR)
        COMPLETED --> [*]
        CANCELED --> [*]
    }

    state "Offer Status" as OFR {
        [*] --> DRAFT: POST /offers
        DRAFT --> SENT: PUT /offers/id/send (HR)
        DRAFT --> [*]: DELETE /offers/id (HR)
        SENT --> ACCEPTED: PUT /offers/id/respond ACCEPT (Candidate)
        SENT --> DECLINED: PUT /offers/id/respond DECLINE (Candidate)
        ACCEPTED --> [*]
        DECLINED --> DRAFT: POST /offers (new offer — second chance)
    }

    state "Job Status" as JOB {
        [*] --> DRAFT: POST /jobs
        DRAFT --> PUBLISHED: PUT /jobs/id/publish → trừ quota
        PUBLISHED --> DRAFT: PUT /jobs/id/unpublish → hoàn quota
        PUBLISHED --> CLOSED: PUT /jobs/id/close → hoàn quota
        CLOSED --> [*]
    }

    state "Payment Status" as PAY {
        [*] --> PENDING: POST /payment/checkout
        PENDING --> PAID: POST /webhooks/payos (PayOS confirm)
        PENDING --> CANCELLED: User hủy trên PayOS
        PENDING --> FAILED: Webhook failure
        PENDING --> EXPIRED: Timeout
        PAID --> [*]
    }

    state "Subscription Status" as SUB {
        [*] --> ACTIVE: Webhook PAID → kích hoạt quota
        ACTIVE --> CANCELLED: PUT /subscriptions/current/cancel
        ACTIVE --> EXPIRED: Cron job — hết expires_at
        EXPIRED --> ACTIVE: Thanh toán gia hạn
        CANCELLED --> ACTIVE: Đăng ký lại
    }