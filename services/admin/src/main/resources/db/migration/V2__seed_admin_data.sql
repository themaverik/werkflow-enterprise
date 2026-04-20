-- Insert default organization
INSERT INTO organizations (name, description, industry, active, created_at, updated_at)
VALUES
    ('Werkflow Corporation', 'Default organization for Werkflow Enterprise Platform', 'Technology', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert system roles
INSERT INTO roles (name, description, type, active, created_at, updated_at)
VALUES
    ('SUPER_ADMIN', 'Super administrator with full system access', 'SYSTEM', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ADMIN', 'Administrator with organization-level access', 'SYSTEM', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MANAGER', 'Manager role with department-level access', 'FUNCTIONAL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('EMPLOYEE', 'Standard employee role', 'FUNCTIONAL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('WORKFLOW_DESIGNER', 'Can design and deploy workflows', 'FUNCTIONAL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert default permissions for SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'organization:read', 'organization:create', 'organization:update', 'organization:delete',
    'department:read', 'department:create', 'department:update', 'department:delete',
    'user:read', 'user:create', 'user:update', 'user:delete',
    'role:read', 'role:create', 'role:update', 'role:delete',
    'workflow:read', 'workflow:create', 'workflow:update', 'workflow:delete', 'workflow:deploy',
    'process:read', 'process:start', 'process:cancel',
    'task:read', 'task:claim', 'task:complete'
]) AS permission
FROM roles WHERE name = 'SUPER_ADMIN';

-- Insert default permissions for ADMIN
INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'department:read', 'department:create', 'department:update',
    'user:read', 'user:create', 'user:update',
    'role:read',
    'workflow:read',
    'process:read', 'process:start',
    'task:read', 'task:claim', 'task:complete'
]) AS permission
FROM roles WHERE name = 'ADMIN';

-- Insert default permissions for WORKFLOW_DESIGNER
INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'workflow:read', 'workflow:create', 'workflow:update', 'workflow:deploy',
    'process:read', 'process:start',
    'task:read'
]) AS permission
FROM roles WHERE name = 'WORKFLOW_DESIGNER';

-- Insert default permissions for MANAGER
INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'department:read',
    'user:read',
    'process:read', 'process:start',
    'task:read', 'task:claim', 'task:complete'
]) AS permission
FROM roles WHERE name = 'MANAGER';

-- Insert default permissions for EMPLOYEE
INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'process:read', 'process:start',
    'task:read', 'task:claim', 'task:complete'
]) AS permission
FROM roles WHERE name = 'EMPLOYEE';

-- Note: Default admin user will be created through Keycloak
-- The user record in this database will be created on first login via Keycloak sync
