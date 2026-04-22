-- ============================================================
-- WERKFLOW ADMIN — OSS BASELINE SCHEMA
-- ============================================================
-- Single consolidated migration replacing all prior incremental
-- migrations. Covers: core admin tables, tenants, service
-- registry, tenant credentials, and role permissions.
-- Enterprise features (departments seed, custody mappings,
-- connector seed) are handled in werkflow-enterprise migrations.
-- ============================================================

-- ============================================================
-- CORE ADMIN TABLES
-- ============================================================

CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    industry VARCHAR(50),
    tax_id VARCHAR(20),
    address VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    country VARCHAR(20),
    postal_code VARCHAR(20),
    phone VARCHAR(20),
    email VARCHAR(100),
    website VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    description VARCHAR(500),
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    parent_department_id BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    manager_user_id VARCHAR(100),
    location VARCHAR(200),
    phone VARCHAR(20),
    email VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, code)
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL DEFAULT 'FUNCTIONAL',
    organization_id BIGINT REFERENCES organizations(id) ON DELETE CASCADE,
    department_id BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role_type CHECK (type IN ('SYSTEM', 'FUNCTIONAL', 'DEPARTMENTAL'))
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(100) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    mobile VARCHAR(20),
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    department_id BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    job_title VARCHAR(100),
    employee_id VARCHAR(50),
    manager_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    hire_date DATE,
    address VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    country VARCHAR(20),
    postal_code VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_departments_organization ON departments(organization_id);
CREATE INDEX idx_departments_parent ON departments(parent_department_id);
CREATE INDEX idx_roles_organization ON roles(organization_id);
CREATE INDEX idx_roles_type ON roles(type);
CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_users_department ON users(department_id);
CREATE INDEX idx_users_manager ON users(manager_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);

-- Seed default organization and system roles
INSERT INTO organizations (name, description, industry, active)
VALUES ('Werkflow Organisation', 'Default organisation for Werkflow platform', 'Technology', TRUE);

INSERT INTO roles (name, description, type, active) VALUES
    ('SUPER_ADMIN',       'Super administrator with full system access',    'SYSTEM',     TRUE),
    ('ADMIN',             'Administrator with organisation-level access',   'SYSTEM',     TRUE),
    ('MANAGER',           'Manager role with department-level access',      'FUNCTIONAL', TRUE),
    ('EMPLOYEE',          'Standard employee role',                         'FUNCTIONAL', TRUE),
    ('WORKFLOW_DESIGNER', 'Can design and deploy workflows',                'FUNCTIONAL', TRUE);

INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'organization:read', 'organization:create', 'organization:update', 'organization:delete',
    'department:read',   'department:create',   'department:update',   'department:delete',
    'user:read',         'user:create',         'user:update',         'user:delete',
    'role:read',         'role:create',         'role:update',         'role:delete',
    'workflow:read',     'workflow:create',      'workflow:update',     'workflow:delete', 'workflow:deploy',
    'process:read',      'process:start',        'process:cancel',
    'task:read',         'task:claim',           'task:complete'
]) FROM roles WHERE name = 'SUPER_ADMIN';

INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'department:read', 'department:create', 'department:update',
    'user:read',       'user:create',       'user:update',
    'role:read',
    'workflow:read',
    'process:read', 'process:start',
    'task:read',    'task:claim',    'task:complete'
]) FROM roles WHERE name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'workflow:read', 'workflow:create', 'workflow:update', 'workflow:deploy',
    'process:read',  'process:start',
    'task:read'
]) FROM roles WHERE name = 'WORKFLOW_DESIGNER';

INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'department:read',
    'user:read',
    'process:read', 'process:start',
    'task:read',    'task:claim',    'task:complete'
]) FROM roles WHERE name = 'MANAGER';

INSERT INTO role_permissions (role_id, permission)
SELECT id, unnest(ARRAY[
    'process:read', 'process:start',
    'task:read',    'task:claim',    'task:complete'
]) FROM roles WHERE name = 'EMPLOYEE';

-- ============================================================
-- TENANT TABLES
-- ============================================================

CREATE TABLE tenants (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_code              VARCHAR(50)  NOT NULL UNIQUE,
    name                     VARCHAR(100) NOT NULL,
    keycloak_realm           VARCHAR(100),
    cross_dept_doa_threshold INT          NOT NULL DEFAULT 4,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_code ON tenants (tenant_code);
CREATE INDEX idx_tenants_active ON tenants (active) WHERE active = TRUE;

-- Add tenant_code and doa_level to core tables
ALTER TABLE organizations ADD COLUMN tenant_code VARCHAR(50);
ALTER TABLE departments   ADD COLUMN tenant_code VARCHAR(50);
ALTER TABLE users         ADD COLUMN tenant_code VARCHAR(50);
ALTER TABLE users         ADD COLUMN doa_level   INT;

CREATE INDEX idx_organizations_tenant ON organizations (tenant_code);
CREATE INDEX idx_departments_tenant   ON departments   (tenant_code);
CREATE INDEX idx_users_tenant         ON users         (tenant_code);
CREATE INDEX idx_users_doa_level      ON users         (doa_level);

-- Seed default tenant and backfill
INSERT INTO tenants (tenant_code, name, cross_dept_doa_threshold, active)
VALUES ('default', 'Default Organisation', 4, TRUE)
ON CONFLICT (tenant_code) DO NOTHING;

UPDATE organizations SET tenant_code = 'default' WHERE tenant_code IS NULL;
UPDATE departments   SET tenant_code = 'default' WHERE tenant_code IS NULL;
UPDATE users         SET tenant_code = 'default' WHERE tenant_code IS NULL;

ALTER TABLE organizations ALTER COLUMN tenant_code SET NOT NULL;
ALTER TABLE departments   ALTER COLUMN tenant_code SET NOT NULL;
ALTER TABLE users         ALTER COLUMN tenant_code SET NOT NULL;

-- ============================================================
-- SERVICE REGISTRY
-- ============================================================

CREATE TABLE IF NOT EXISTS service_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    service_type VARCHAR(50) NOT NULL CHECK (service_type IN ('INTERNAL', 'EXTERNAL', 'THIRD_PARTY')),
    base_path VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    department_id BIGINT,
    owner_user_id BIGINT,
    health_check_url VARCHAR(500),
    last_health_check_at TIMESTAMP WITH TIME ZONE,
    health_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN' CHECK (health_status IN ('HEALTHY', 'UNHEALTHY', 'UNKNOWN', 'DEGRADED')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT fk_service_owner      FOREIGN KEY (owner_user_id) REFERENCES users(id)        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS service_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    description TEXT,
    requires_auth BOOLEAN NOT NULL DEFAULT true,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    retry_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_endpoint_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT unique_service_endpoint_method UNIQUE (service_id, endpoint_path, http_method)
);

CREATE TABLE IF NOT EXISTS service_environment_urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    environment VARCHAR(50) NOT NULL CHECK (environment IN ('development', 'staging', 'production', 'local')),
    base_url VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_env_url_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT uq_service_environment UNIQUE (service_id, environment)
);

CREATE TABLE IF NOT EXISTS service_health_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    environment VARCHAR(50) NOT NULL CHECK (environment IN ('development', 'staging', 'production', 'local')),
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK (status IN ('HEALTHY', 'UNHEALTHY', 'UNKNOWN', 'DEGRADED')),
    response_time_ms INTEGER,
    error_message TEXT,
    CONSTRAINT fk_health_check_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_tags (
    service_id UUID NOT NULL,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (service_id, tag),
    CONSTRAINT fk_tag_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_service_registry_service_name ON service_registry(service_name);
CREATE INDEX IF NOT EXISTS idx_service_registry_service_type ON service_registry(service_type);
CREATE INDEX IF NOT EXISTS idx_service_registry_active       ON service_registry(active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_service_endpoints_service_id  ON service_endpoints(service_id);
CREATE INDEX IF NOT EXISTS idx_service_env_urls_service_env  ON service_environment_urls(service_id, environment);
CREATE INDEX IF NOT EXISTS idx_service_health_checks_service_time ON service_health_checks(service_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_service_tags_tag              ON service_tags(tag);

-- ============================================================
-- TENANT SERVICE CREDENTIALS
-- (used by BPMN engine to call external services via service tasks)
-- ============================================================

CREATE TABLE tenant_service_endpoints (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_code   VARCHAR(50)  NOT NULL,
    service_key   VARCHAR(100) NOT NULL,
    connector_key VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    base_url      VARCHAR(500) NOT NULL,
    environment   VARCHAR(50)  NOT NULL DEFAULT 'development',
    sample_schema JSONB,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tse_tenant_connector_env UNIQUE (tenant_code, connector_key, environment)
);

CREATE INDEX idx_tenant_svc_ep_tenant ON tenant_service_endpoints (tenant_code);

CREATE TABLE tenant_api_credentials (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_code     VARCHAR(50)  NOT NULL,
    credential_key  VARCHAR(100) NOT NULL,
    connector_key   VARCHAR(100) NOT NULL,
    label           VARCHAR(200),
    auth_scheme     VARCHAR(30)  NOT NULL,
    secret_ref      VARCHAR(200) NOT NULL,
    header_name     VARCHAR(100),
    allowed_origins JSONB,
    key_prefix      VARCHAR(20),
    last_used_at    TIMESTAMP,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tac_tenant_connector UNIQUE (tenant_code, connector_key),
    CONSTRAINT chk_auth_scheme CHECK (auth_scheme IN ('API_KEY', 'BEARER', 'BASIC', 'OAUTH2_CLIENT_CREDENTIALS'))
);

CREATE INDEX idx_tenant_api_cred_tenant ON tenant_api_credentials (tenant_code);

-- ============================================================
-- TENANT ROLE PERMISSIONS
-- ============================================================

CREATE TABLE tenant_role_permissions (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_code VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    permission  VARCHAR(100) NOT NULL,
    UNIQUE (tenant_code, role_name, permission)
);

CREATE INDEX idx_trp_tenant_role ON tenant_role_permissions (tenant_code, role_name);
