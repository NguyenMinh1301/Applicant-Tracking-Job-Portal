-- ============================================================
-- VietRecruit | Migration V8__create_role_permissions.sql
-- Description: Junction table mapping roles to permissions
-- Depends on:  roles, permissions
-- ============================================================

CREATE TABLE role_permissions (
    role_id       SMALLINT NOT NULL,
    permission_id SMALLINT NOT NULL,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
