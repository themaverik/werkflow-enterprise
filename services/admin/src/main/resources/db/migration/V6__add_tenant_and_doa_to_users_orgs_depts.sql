-- Add tenant_code to organizations (nullable for migration, constrained in V9 after seed)
ALTER TABLE organizations ADD COLUMN tenant_code VARCHAR(50);

-- Add tenant_code to departments
ALTER TABLE departments ADD COLUMN tenant_code VARCHAR(50);

-- Add tenant_code and doa_level to users
ALTER TABLE users ADD COLUMN tenant_code VARCHAR(50);
ALTER TABLE users ADD COLUMN doa_level    INT;

-- Index now; FK constraints to tenants.tenant_code added in V9 after seed data backfills values
CREATE INDEX idx_organizations_tenant ON organizations (tenant_code);
CREATE INDEX idx_departments_tenant   ON departments   (tenant_code);
CREATE INDEX idx_users_tenant         ON users         (tenant_code);
CREATE INDEX idx_users_doa_level      ON users         (doa_level);
