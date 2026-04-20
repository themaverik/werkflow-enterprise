-- Add missing columns to approval_thresholds table
ALTER TABLE approval_thresholds ADD COLUMN description VARCHAR(500);

-- Add missing columns to budget_categories table if needed
ALTER TABLE budget_categories ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- Add category_id column to approval_thresholds if missing
ALTER TABLE approval_thresholds ADD COLUMN IF NOT EXISTS category_id BIGINT REFERENCES budget_categories(id);
