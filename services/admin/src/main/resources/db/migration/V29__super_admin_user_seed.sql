-- Seeds the platform super admin user for fresh deployments.
-- Idempotent: ON CONFLICT DO NOTHING — safe to run on every restart.
-- Resolves organization_id and role_id by name (not hardcoded numeric ID).
-- 'Werkflow Organisation' is seeded in V1. 'SUPER_ADMIN' role is seeded in V1.

INSERT INTO users (
    keycloak_id,
    username,
    email,
    first_name,
    last_name,
    organization_id,
    tenant_code,
    doa_level,
    active,
    email_verified,
    created_at,
    updated_at
)
SELECT
    'admin',
    'admin',
    'admin@werkflow.internal',
    'Admin',
    'User',
    o.id,
    'default',
    4,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM organizations o
WHERE o.tenant_code = 'default'
ON CONFLICT (keycloak_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.keycloak_id = 'admin'
  AND r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
