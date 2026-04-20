-- V4: Redesign employees table for DoA, multi-tenancy, optional department

-- Add new columns
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS organization_id BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS keycloak_user_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gender VARCHAR(30),
    ADD COLUMN IF NOT EXISTS profile_photo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS doa_level INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS office_location VARCHAR(50),
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

-- Make department_id nullable (employees can exist without a department)
ALTER TABLE employees
    ALTER COLUMN department_id DROP NOT NULL;

-- Drop manager_id self-reference (not in new model)
ALTER TABLE employees
    DROP COLUMN IF EXISTS manager_id;

-- Add constraints
ALTER TABLE employees
    ADD CONSTRAINT chk_doa_level CHECK (doa_level >= 0 AND doa_level <= 4),
    ADD CONSTRAINT chk_gender CHECK (
        gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY') OR gender IS NULL
    ),
    ADD CONSTRAINT chk_emp_location CHECK (
        office_location IN ('SEATTLE_US', 'BANGALORE_IN', 'SHILLONG_IN', 'STOCKHOLM_SE', 'MELBOURNE_AU')
        OR office_location IS NULL
    );

-- Unique Keycloak link
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_keycloak ON employees(keycloak_user_id)
    WHERE keycloak_user_id IS NOT NULL;

-- Index for org + dept lookups
CREATE INDEX IF NOT EXISTS idx_employee_org ON employees(organization_id);
CREATE INDEX IF NOT EXISTS idx_employee_dept_code ON employees(department_code);
CREATE INDEX IF NOT EXISTS idx_employee_doa ON employees(doa_level);

COMMENT ON COLUMN employees.doa_level IS '0=none, 1=basic, 2=dept-head, 3=global, 4=c-suite';
COMMENT ON COLUMN employees.keycloak_user_id IS 'Links to Keycloak identity (sub claim)';
COMMENT ON COLUMN employees.department_code IS 'Nullable — C-suite etc. may not belong to a dept';
