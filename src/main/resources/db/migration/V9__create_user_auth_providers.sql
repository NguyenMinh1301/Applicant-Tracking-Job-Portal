-- ============================================================
-- VietRecruit | Migration V9__create_user_auth_providers.sql
-- Description: OAuth2 provider links per user (Google, GitHub)
-- Depends on:  users
-- ============================================================

CREATE TABLE user_auth_providers (
    -- Primary key
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    user_id             UUID         NOT NULL,

    -- Business columns
    provider            VARCHAR(20)  NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    provider_email      VARCHAR(255),
    provider_name       VARCHAR(255),
    provider_avatar_url VARCHAR(512),
    linked_at           TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_uap_user       FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_provider  UNIQUE (user_id, provider),
    CONSTRAINT uq_provider_user  UNIQUE (provider, provider_user_id)
);

-- Indexes
CREATE INDEX idx_uap_user_id         ON user_auth_providers(user_id);
CREATE INDEX idx_uap_provider_lookup ON user_auth_providers(provider, provider_user_id);

COMMENT ON TABLE user_auth_providers IS 'OAuth2 identity provider links per user account';
