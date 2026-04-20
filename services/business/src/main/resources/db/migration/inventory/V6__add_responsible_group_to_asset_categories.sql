-- V6: Add responsible_group to asset_categories for new-format group routing
ALTER TABLE inventory_service.asset_categories
    ADD COLUMN responsible_group VARCHAR(50);

UPDATE inventory_service.asset_categories SET responsible_group = 'DEPT:IT'  WHERE code = 'IT';
UPDATE inventory_service.asset_categories SET responsible_group = 'DEPT:OPS' WHERE code = 'OFFICE';
UPDATE inventory_service.asset_categories SET responsible_group = 'DEPT:OPS' WHERE code = 'VEH';
