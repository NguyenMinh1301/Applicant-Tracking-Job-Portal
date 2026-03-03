-- Customer Service role
INSERT INTO roles (code, name) VALUES ('CUSTOMER_SERVICE', 'Customer Service');

-- Payment transaction viewing permission
INSERT INTO permissions (code, name, module) VALUES
    ('TRANSACTION:VIEW_ALL', 'View All Transaction Records', 'PAYMENT');

-- Assign TRANSACTION:VIEW_ALL to CUSTOMER_SERVICE
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'CUSTOMER_SERVICE' AND p.code = 'TRANSACTION:VIEW_ALL';

-- Also assign TRANSACTION:VIEW_ALL to SYSTEM_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code = 'SYSTEM_ADMIN' AND p.code = 'TRANSACTION:VIEW_ALL';
