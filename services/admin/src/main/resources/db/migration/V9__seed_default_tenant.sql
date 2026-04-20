-- V9: Seed the default tenant and backfill tenant_code on all existing rows

-- Insert default tenant (idempotent)
INSERT INTO tenants (tenant_code, name, cross_dept_doa_threshold, active)
VALUES ('default', 'Default Organisation', 4, TRUE)
ON CONFLICT (tenant_code) DO NOTHING;

-- Backfill all existing rows
UPDATE organizations SET tenant_code = 'default' WHERE tenant_code IS NULL;
UPDATE departments   SET tenant_code = 'default' WHERE tenant_code IS NULL;
UPDATE users         SET tenant_code = 'default' WHERE tenant_code IS NULL;

-- Enforce NOT NULL now that backfill is complete
ALTER TABLE organizations ALTER COLUMN tenant_code SET NOT NULL;
ALTER TABLE departments   ALTER COLUMN tenant_code SET NOT NULL;
ALTER TABLE users         ALTER COLUMN tenant_code SET NOT NULL;
