-- ============================================================
-- VietRecruit | Migration V10__create_refresh_tokens.sql
-- Description: JWT refresh token storage for session management
-- Depends on:  users
-- ============================================================

CREATE TABLE refresh_tokens (
    -- Primary key
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    user_id     UUID        NOT NULL,

    -- Business columns
    token_hash  VARCHAR(64) NOT NULL,
    device_info VARCHAR(255),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     DEFAULT FALSE,

    -- Audit columns
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked = FALSE;

COMMENT ON TABLE  refresh_tokens            IS 'Hashed refresh tokens for stateless JWT rotation';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the actual token value';
