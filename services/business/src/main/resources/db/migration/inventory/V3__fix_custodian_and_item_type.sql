-- V3: Replace primary_custodian_dept_id FK with custodian_dept_code VARCHAR
--     Add item_type to asset_definitions

-- asset_categories: add custodian_dept_code and custodian_user_id
ALTER TABLE inventory_service.asset_categories
    ADD COLUMN IF NOT EXISTS custodian_dept_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS custodian_user_id VARCHAR(100);

-- Migrate existing data: set custodian_dept_code based on primary_custodian_dept_id
UPDATE inventory_service.asset_categories SET custodian_dept_code = CASE
    WHEN primary_custodian_dept_id = 1 THEN 'IT'
    WHEN primary_custodian_dept_id = 2 THEN 'OPS'
    WHEN primary_custodian_dept_id = 3 THEN 'OPS'
    ELSE 'IT'
END WHERE primary_custodian_dept_id IS NOT NULL;

-- Drop NOT NULL constraint before dropping column
ALTER TABLE inventory_service.asset_categories
    ALTER COLUMN primary_custodian_dept_id DROP NOT NULL;

-- Drop old dept ID column
ALTER TABLE inventory_service.asset_categories
    DROP COLUMN IF EXISTS primary_custodian_dept_id;

-- asset_definitions: add item_type
ALTER TABLE inventory_service.asset_definitions
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';

ALTER TABLE inventory_service.asset_definitions
    ADD CONSTRAINT chk_item_type CHECK (item_type IN ('INDIVIDUAL', 'BULK'));

-- custody_records: add Keycloak user ID column
ALTER TABLE inventory_service.custody_records
    ADD COLUMN IF NOT EXISTS custodian_keycloak_id VARCHAR(100);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_asset_cat_dept_code ON inventory_service.asset_categories(custodian_dept_code);
CREATE INDEX IF NOT EXISTS idx_asset_def_item_type ON inventory_service.asset_definitions(item_type);
