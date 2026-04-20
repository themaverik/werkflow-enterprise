-- V5: Reseed asset categories and definitions per spec

-- Clear existing seed data (no referencing rows in asset_instances or custody_records)
DELETE FROM inventory_service.asset_definitions;
DELETE FROM inventory_service.asset_categories;

-- Reset sequences
ALTER SEQUENCE inventory_service.asset_categories_id_seq RESTART WITH 1;
ALTER SEQUENCE inventory_service.asset_definitions_id_seq RESTART WITH 1;

-- Root categories
INSERT INTO inventory_service.asset_categories (name, code, description, custodian_dept_code, requires_approval, active)
VALUES
    ('IT',            'IT',     'Information Technology equipment',   'IT',  true,  true),
    ('Office Assets', 'OFFICE', 'Office furniture and equipment',     'OPS', true,  true),
    ('Vehicles',      'VEH',    'Company vehicles',                   'OPS', true,  true);

-- IT subcategories
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Laptop',       'IT-LAP',  'Laptop computers (individual assignment)',          'IT', true,  true FROM inventory_service.asset_categories WHERE code='IT';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Monitor',      'IT-MON',  'Monitors and displays (individual assignment)',     'IT', false, true FROM inventory_service.asset_categories WHERE code='IT';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Mobile',       'IT-MOB',  'Mobile phones (individual assignment)',             'IT', true,  true FROM inventory_service.asset_categories WHERE code='IT';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Tablet',       'IT-TAB',  'Tablets (individual assignment)',                   'IT', true,  true FROM inventory_service.asset_categories WHERE code='IT';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Accessories',  'IT-ACC',  'IT accessories and consumables (bulk stock)',       'IT', false, true FROM inventory_service.asset_categories WHERE code='IT';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Camera',       'IT-CAM',  'Cameras and photography equipment',                'IT', true,  true FROM inventory_service.asset_categories WHERE code='IT';

-- Office Assets subcategories
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Workstation',   'OFF-WS',    'Workstations (bulk)',            'OPS', true,  true FROM inventory_service.asset_categories WHERE code='OFFICE';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Table',         'OFF-TBL',   'Tables and desks (bulk)',        'OPS', false, true FROM inventory_service.asset_categories WHERE code='OFFICE';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Office Chair',  'OFF-CHAIR', 'Ergonomic office chairs',        'OPS', false, true FROM inventory_service.asset_categories WHERE code='OFFICE';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, 'Chair',         'OFF-CHR',   'General purpose chairs (bulk)',  'OPS', false, true FROM inventory_service.asset_categories WHERE code='OFFICE';

-- Vehicles subcategories
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, '4-Wheeler', 'VEH-4W', 'Cars and 4-wheel vehicles',        'OPS', true, true FROM inventory_service.asset_categories WHERE code='VEH';
INSERT INTO inventory_service.asset_categories (parent_category_id, name, code, description, custodian_dept_code, requires_approval, active)
SELECT id, '2-Wheeler', 'VEH-2W', 'Motorcycles and 2-wheel vehicles', 'OPS', true, true FROM inventory_service.asset_categories WHERE code='VEH';

-- Asset definitions (INDIVIDUAL items — include expected_lifespan_months)
INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'IT-LAP-001', 'Apple MacBook Pro 16"', 'Apple', 'MacBook Pro 16 M3 Pro 2023',
    'INDIVIDUAL', 2999.00, 48, '{"cpu":"M3 Pro","ram":"36GB","storage":"1TB SSD"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-LAP';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'IT-LAP-002', 'Dell XPS 15', 'Dell', 'XPS 15 9530',
    'INDIVIDUAL', 1899.00, 48, '{"cpu":"Intel i7-13700H","ram":"32GB","storage":"1TB SSD"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-LAP';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'IT-MON-001', 'Dell 27" Monitor', 'Dell', 'U2723DE',
    'INDIVIDUAL', 599.00, 60, '{"size":"27-inch","resolution":"2560x1440","panel":"IPS"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-MON';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'IT-MOB-001', 'Apple iPhone 15 Pro', 'Apple', 'iPhone 15 Pro 256GB',
    'INDIVIDUAL', 999.00, 36, '{"storage":"256GB","color":"Natural Titanium","network":"5G"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-MOB';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'IT-TAB-001', 'Apple iPad Pro 12.9"', 'Apple', 'iPad Pro M2 12.9-inch',
    'INDIVIDUAL', 1099.00, 48, '{"storage":"256GB","connectivity":"WiFi+Cellular"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-TAB';

-- Asset definitions (BULK items — no expected_lifespan_months)
INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, specifications, active)
SELECT id, 'IT-ACC-001', 'Apple USB-C Charger 96W', 'Apple', 'MXOT2LL/A',
    'BULK', 79.00, '{"wattage":"96W","connector":"USB-C"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-ACC';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, specifications, active)
SELECT id, 'IT-ACC-002', 'USB Flash Drive 64GB', 'SanDisk', 'Ultra USB 3.0',
    'BULK', 12.00, '{"capacity":"64GB","interface":"USB 3.0"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-ACC';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, specifications, active)
SELECT id, 'IT-ACC-003', '1TB SSD', 'Samsung', '870 EVO',
    'BULK', 89.00, '{"capacity":"1TB","interface":"SATA III"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-ACC';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, specifications, active)
SELECT id, 'IT-ACC-004', 'Mobile USB Charger', 'Anker', 'PowerPort III 20W',
    'BULK', 15.00, '{"wattage":"20W","connector":"USB-C"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='IT-ACC';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, expected_lifespan_months, specifications, active)
SELECT id, 'OFF-CHAIR-001', 'Ergonomic Office Chair', 'Herman Miller', 'Aeron',
    'INDIVIDUAL', 1395.00, 120, '{"type":"Task Chair","adjustable_arms":true}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='OFF-CHAIR';

INSERT INTO inventory_service.asset_definitions (category_id, sku, name, manufacturer, model, item_type, unit_cost, specifications, active)
SELECT id, 'OFF-TBL-001', 'Adjustable Standing Desk', 'Autonomous', 'SmartDesk Pro',
    'BULK', 599.00, '{"dimensions":"60x30 inches","height_range":"29-48 inches"}'::jsonb, true
FROM inventory_service.asset_categories WHERE code='OFF-TBL';
