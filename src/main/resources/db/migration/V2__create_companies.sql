-- ============================================================
-- VietRecruit | Migration V2__create_companies.sql
-- Description: Employer company profiles
-- Depends on:  none
-- ============================================================

CREATE TABLE companies (
    -- Primary key
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Business columns
    name       VARCHAR(255) NOT NULL,
    domain     VARCHAR(255),
    website    VARCHAR(255),

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    -- Named constraints
    CONSTRAINT uq_companies_domain UNIQUE (domain)
);

COMMENT ON TABLE  companies            IS 'Employer organizations registered on the platform';
COMMENT ON COLUMN companies.domain     IS 'Unique corporate email domain for auto-association';
COMMENT ON COLUMN companies.deleted_at IS 'Soft-delete marker';
