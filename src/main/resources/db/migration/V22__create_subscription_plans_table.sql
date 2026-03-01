-- Subscription status enum
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'EXPIRED', 'CANCELLED');

-- Subscription plans catalog
CREATE TABLE subscription_plans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(50) UNIQUE NOT NULL,
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    max_active_jobs   INTEGER NOT NULL DEFAULT 1,
    job_duration_days INTEGER NOT NULL DEFAULT 30,
    resume_access     BOOLEAN DEFAULT FALSE,
    ai_matching       BOOLEAN DEFAULT FALSE,
    priority_listing  BOOLEAN DEFAULT FALSE,
    price_monthly     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_yearly      NUMERIC(12, 2),
    currency          VARCHAR(10) DEFAULT 'VND',
    is_active         BOOLEAN DEFAULT TRUE,
    created_at        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Seed default plans (-1 = unlimited)
INSERT INTO subscription_plans (code, name, description, max_active_jobs, job_duration_days, resume_access, ai_matching, priority_listing, price_monthly) VALUES
    ('FREE',       'Free',       'Get started with basic job posting',            1,  15, FALSE, FALSE, FALSE, 0),
    ('BASIC',      'Basic',      'Essential tools for growing teams',             5,  30, TRUE,  FALSE, FALSE, 500000),
    ('PREMIUM',    'Premium',    'Advanced features for active recruiters',       20, 60, TRUE,  TRUE,  TRUE,  1500000),
    ('ENTERPRISE', 'Enterprise', 'Unlimited access for large-scale hiring',       -1, 90, TRUE,  TRUE,  TRUE,  5000000);
