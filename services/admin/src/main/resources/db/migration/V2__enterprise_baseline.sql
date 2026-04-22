-- ============================================================
-- WERKFLOW ADMIN — ENTERPRISE BASELINE
-- ============================================================
-- Adds enterprise-only tables and seed data on top of the OSS
-- V1__baseline.sql. Covers: tenant custody mappings, bootstrap
-- departments, and the webhook-test connector.
--
-- Applies after: V1__baseline.sql (OSS)
-- ============================================================

-- ============================================================
-- TENANT CUSTODY MAPPINGS
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant_custody_mappings (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_code  VARCHAR(50)  NOT NULL,
    category_key VARCHAR(100) NOT NULL,
    custody_group VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tcm_tenant_category UNIQUE (tenant_code, category_key)
);

CREATE INDEX IF NOT EXISTS idx_tcm_tenant ON tenant_custody_mappings (tenant_code);

-- ============================================================
-- BOOTSTRAP DEPARTMENTS (Engineering, Finance, HR, Ops, IT)
-- Seeded for the default tenant / Werkflow Organisation.
-- ============================================================

INSERT INTO departments (organization_id, name, code, description, tenant_code, active, created_at, updated_at)
SELECT
    o.id,
    d.dept_name,
    d.dept_code,
    d.dept_desc,
    'default',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM organizations o
CROSS JOIN (VALUES
    ('Engineering', 'ENG', 'Engineering and Product'),
    ('Finance',     'FIN', 'Finance and Accounting'),
    ('HR',          'HR',  'Human Resources'),
    ('Operations',  'OPS', 'Operations'),
    ('IT',          'IT',  'Information Technology')
) AS d(dept_name, dept_code, dept_desc)
WHERE o.name = 'Werkflow Organisation'
ON CONFLICT (organization_id, code) DO NOTHING;

-- ============================================================
-- WEBHOOK TEST CONNECTOR
-- Points to httpbin.org for testing connector-based service tasks.
-- ============================================================

INSERT INTO tenant_service_endpoints (tenant_code, service_key, connector_key, display_name, base_url, environment, active, created_at, updated_at)
VALUES (
    'default',
    'webhook-test',
    'webhook-test',
    'Webhook Test (httpbin.org)',
    'https://httpbin.org',
    'development',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT ON CONSTRAINT uq_tse_tenant_connector_env DO NOTHING;
