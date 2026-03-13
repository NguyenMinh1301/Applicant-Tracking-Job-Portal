-- ============================================================
-- VietRecruit | Migration V4__create_permissions.sql
-- Description: RBAC permissions lookup table
-- Depends on:  none
-- ============================================================

CREATE TABLE permissions (
    -- Primary key (SMALLSERIAL — matches JPA GenerationType.IDENTITY / Short)
    id         SMALLSERIAL PRIMARY KEY,

    -- Business columns
    code       VARCHAR(100) UNIQUE NOT NULL,
    name       VARCHAR(150) NOT NULL,
    module     VARCHAR(50)  NOT NULL,

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_module ON permissions(module);

COMMENT ON TABLE  permissions        IS 'Granular permission entries grouped by module';
COMMENT ON COLUMN permissions.code   IS 'MODULE:ACTION format (e.g. JOB:CREATE)';
COMMENT ON COLUMN permissions.module IS 'Logical grouping for UI and authorization filtering';
