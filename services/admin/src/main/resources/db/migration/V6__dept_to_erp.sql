-- ADR-005: departments moved to werkflow-erp (P1.6.3).
-- Migrate existing department_id FK → department_code string, then drop the departments table.

-- Step 1: add department_code column to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS department_code VARCHAR(50);

-- Step 2: backfill from departments table
UPDATE users u
SET department_code = d.code
FROM departments d
WHERE u.department_id = d.id
  AND u.department_id IS NOT NULL;

-- Step 3: remove FK column
ALTER TABLE users DROP COLUMN IF EXISTS department_id;

-- Step 4: drop the departments table — ERP is now source of truth.
-- CASCADE drops FK constraints in roles/users/service_registry that reference this table
-- so that this migration succeeds on a fresh schema (e.g. Testcontainers IT databases).
-- Live databases that already applied V6 require: mvn flyway:repair (updates stored checksum).
DROP TABLE IF EXISTS departments CASCADE;
