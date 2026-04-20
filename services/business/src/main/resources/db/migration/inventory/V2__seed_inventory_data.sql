-- Insert default asset categories
-- NOTE: primary_custodian_dept_id values assume default organization has these departments

-- IT Equipment (assuming IT dept id = 1 from admin_service)
INSERT INTO asset_categories (name, code, description, primary_custodian_dept_id, requires_approval, active, created_at, updated_at)
VALUES
    ('IT Equipment', 'IT', 'Information Technology equipment and devices', 1, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Furniture', 'FURN', 'Office furniture and fixtures', 2, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Office Equipment', 'OFFICE', 'General office equipment', 2, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Vehicles', 'VEHICLE', 'Company vehicles and transportation', 3, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- IT Equipment subcategories
INSERT INTO asset_categories (parent_category_id, name, code, description, primary_custodian_dept_id, requires_approval, active, created_at, updated_at)
SELECT id, 'Laptops', 'IT-LAP', 'Laptop computers', primary_custodian_dept_id, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT';

INSERT INTO asset_categories (parent_category_id, name, code, description, primary_custodian_dept_id, requires_approval, active, created_at, updated_at)
SELECT id, 'Desktops', 'IT-DESK', 'Desktop computers', primary_custodian_dept_id, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT';

INSERT INTO asset_categories (parent_category_id, name, code, description, primary_custodian_dept_id, requires_approval, active, created_at, updated_at)
SELECT id, 'Monitors', 'IT-MON', 'Computer monitors and displays', primary_custodian_dept_id, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT';

INSERT INTO asset_categories (parent_category_id, name, code, description, primary_custodian_dept_id, requires_approval, active, created_at, updated_at)
SELECT id, 'Mobile Devices', 'IT-MOB', 'Smartphones and tablets', primary_custodian_dept_id, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT';

-- Sample asset definitions
-- Laptops
INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'LAP-MBP16-01', 'MacBook Pro 16"', 'Apple', 'MacBook Pro 2023',
    '{"cpu": "M3 Pro", "ram": "32GB", "storage": "1TB SSD", "display": "16-inch Liquid Retina XDR"}'::jsonb,
    2999.00, 48, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT-LAP';

INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'LAP-DELL-01', 'Dell XPS 15', 'Dell', 'XPS 15 9530',
    '{"cpu": "Intel i7-13700H", "ram": "32GB", "storage": "1TB SSD", "display": "15.6-inch FHD+"}'::jsonb,
    1899.00, 48, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT-LAP';

-- Monitors
INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'MON-DELL-27', 'Dell 27" Monitor', 'Dell', 'U2723DE',
    '{"size": "27-inch", "resolution": "2560x1440", "refresh_rate": "60Hz", "panel": "IPS"}'::jsonb,
    599.00, 60, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT-MON';

-- Mobile Devices
INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'MOB-IPH15-01', 'iPhone 15 Pro', 'Apple', 'iPhone 15 Pro',
    '{"storage": "256GB", "color": "Natural Titanium", "network": "5G"}'::jsonb,
    999.00, 36, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'IT-MOB';

-- Furniture
INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'FURN-CHAIR-ERG', 'Ergonomic Office Chair', 'Herman Miller', 'Aeron',
    '{"type": "Task Chair", "adjustable_arms": true, "lumbar_support": true, "material": "Mesh"}'::jsonb,
    1395.00, 120, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'FURN';

INSERT INTO asset_definitions (category_id, sku, name, manufacturer, model, specifications, unit_cost, expected_lifespan_months, requires_maintenance, active, created_at, updated_at)
SELECT id, 'FURN-DESK-ADJ', 'Adjustable Standing Desk', 'Autonomous', 'SmartDesk Pro',
    '{"dimensions": "60x30 inches", "height_range": "29-48 inches", "motor": "Dual motor", "material": "Bamboo top"}'::jsonb,
    599.00, 120, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM asset_categories WHERE code = 'FURN';
