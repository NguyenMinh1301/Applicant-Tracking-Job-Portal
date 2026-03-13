-- ============================================================
-- VietRecruit | Migration V6__create_users.sql
-- Description: Platform user accounts (all roles)
-- Depends on:  companies
-- ============================================================

CREATE TABLE users (
    -- Primary key
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id        UUID,

    -- Business columns
    email             VARCHAR(255) UNIQUE NOT NULL,
    password_hash     VARCHAR(255),
    full_name         VARCHAR(255) NOT NULL,
    phone             VARCHAR(50),
    avatar_url        VARCHAR(500),
    banner_url        VARCHAR(500),
    avatar_object_key VARCHAR(500),
    banner_object_key VARCHAR(500),
    linkedin_url      VARCHAR(255),
    github_url        VARCHAR(255),
    portfolio_url     VARCHAR(255),
    location          VARCHAR(255),
    dob               DATE,
    gender            VARCHAR(20),
    email_verified    BOOLEAN     DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    is_active         BOOLEAN     DEFAULT TRUE,
    is_locked         BOOLEAN     DEFAULT FALSE,
    failed_attempts   SMALLINT    DEFAULT 0,
    lock_until        TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,

    -- Audit columns
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,

    -- Named constraints
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_users_company_id      ON users(company_id);
CREATE INDEX idx_users_email           ON users(email);
CREATE INDEX idx_users_email_verified  ON users(id) WHERE email_verified = FALSE;
CREATE INDEX idx_users_active          ON users(is_active) WHERE deleted_at IS NULL;

COMMENT ON TABLE  users                IS 'All platform users: admins, HR, interviewers, and candidates';
COMMENT ON COLUMN users.company_id     IS 'NULL for SYSTEM_ADMIN and CANDIDATE users';
COMMENT ON COLUMN users.password_hash  IS 'BCrypt hash; NULL for OAuth2-only accounts';
COMMENT ON COLUMN users.is_active      IS 'Soft disable without deletion';
COMMENT ON COLUMN users.failed_attempts IS 'Brute-force protection counter';
