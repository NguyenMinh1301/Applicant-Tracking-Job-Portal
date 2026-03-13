-- ============================================================
-- VietRecruit | Migration V3__create_roles.sql
-- Description: RBAC roles lookup table
-- Depends on:  none
-- ============================================================

CREATE TABLE roles (
    -- Primary key (SMALLSERIAL — matches JPA GenerationType.IDENTITY / Short)
    id         SMALLSERIAL PRIMARY KEY,

    -- Business columns
    code       VARCHAR(50)  UNIQUE NOT NULL,
    name       VARCHAR(100) NOT NULL,

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  roles      IS 'System-wide authorization roles';
COMMENT ON COLUMN roles.code IS 'Machine-readable role identifier (e.g. SYSTEM_ADMIN)';
