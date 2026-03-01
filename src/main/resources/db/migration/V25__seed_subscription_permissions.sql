-- Subscription-related permissions
INSERT INTO permissions (code, name, module) VALUES
    ('PLAN:VIEW',            'View Plans',              'PLAN'),
    ('PLAN:MANAGE',          'Manage Plans',            'PLAN'),
    ('SUBSCRIPTION:VIEW',    'View Subscriptions',      'SUBSCRIPTION'),
    ('SUBSCRIPTION:MANAGE',  'Manage Subscriptions',    'SUBSCRIPTION');

-- SYSTEM_ADMIN (id=1): grant all new permissions
INSERT INTO role_permissions (role_id, permission_id)
    SELECT 1, id FROM permissions WHERE module IN ('PLAN', 'SUBSCRIPTION');

-- COMPANY_ADMIN (id=2): view plans + view/manage own subscription
INSERT INTO role_permissions (role_id, permission_id)
    SELECT 2, id FROM permissions WHERE code IN ('PLAN:VIEW', 'SUBSCRIPTION:VIEW', 'SUBSCRIPTION:MANAGE');

-- HR (id=3): view plans + view subscription
INSERT INTO role_permissions (role_id, permission_id)
    SELECT 3, id FROM permissions WHERE code IN ('PLAN:VIEW', 'SUBSCRIPTION:VIEW');
