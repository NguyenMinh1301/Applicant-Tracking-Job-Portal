-- ============================================================
-- VietRecruit | Migration V7__create_user_roles.sql
-- Description: Junction table mapping users to roles (RBAC)
-- Depends on:  users, roles
-- ============================================================

CREATE TABLE user_roles (
    user_id UUID     NOT NULL,
    role_id SMALLINT NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);
