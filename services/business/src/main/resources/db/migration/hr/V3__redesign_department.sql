-- V3: Redesign departments table for multi-tenancy and remove hierarchy

-- Add new columns
ALTER TABLE departments
    ADD COLUMN IF NOT EXISTS organization_id BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS department_type VARCHAR(20) NOT NULL DEFAULT 'OPS',
    ADD COLUMN IF NOT EXISTS lead_user_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS office_location VARCHAR(50),
    ADD COLUMN IF NOT EXISTS department_email VARCHAR(150);

-- Drop hierarchy columns
ALTER TABLE departments
    DROP COLUMN IF EXISTS parent_department_id,
    DROP COLUMN IF EXISTS description;

-- Drop old global uniqueness constraints
ALTER TABLE departments DROP CONSTRAINT IF EXISTS departments_name_key;

-- Add per-org uniqueness
ALTER TABLE departments
    ADD CONSTRAINT uq_dept_code_per_org UNIQUE (organization_id, code),
    ADD CONSTRAINT uq_dept_name_per_org UNIQUE (organization_id, name);

-- Add constraint for department_type values
ALTER TABLE departments
    ADD CONSTRAINT chk_dept_type CHECK (department_type IN ('OPS', 'DATA'));

-- Add constraint for office_location values
ALTER TABLE departments
    ADD CONSTRAINT chk_dept_location CHECK (
        office_location IN ('SEATTLE_US', 'BANGALORE_IN', 'SHILLONG_IN', 'STOCKHOLM_SE', 'MELBOURNE_AU')
        OR office_location IS NULL
    );

-- Index for org lookup
CREATE INDEX IF NOT EXISTS idx_dept_org ON departments(organization_id);
CREATE INDEX IF NOT EXISTS idx_dept_org_code ON departments(organization_id, code);

COMMENT ON COLUMN departments.organization_id IS 'Multi-tenant: references admin_service.organizations.id';
COMMENT ON COLUMN departments.lead_user_id IS 'Keycloak user ID of department head/lead (single point of contact)';
COMMENT ON COLUMN departments.department_type IS 'OPS or DATA';
