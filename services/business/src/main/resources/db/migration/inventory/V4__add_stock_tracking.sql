-- V4: Add stock_locations and inventory_stock tables for BULK item tracking

CREATE TABLE IF NOT EXISTS inventory_service.stock_locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    office_location VARCHAR(50) NOT NULL,
    department_code VARCHAR(50),
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_office_location CHECK (
        office_location IN ('SEATTLE_US', 'BANGALORE_IN', 'SHILLONG_IN', 'STOCKHOLM_SE', 'MELBOURNE_AU')
    ),
    UNIQUE(name, office_location)
);

CREATE TABLE IF NOT EXISTS inventory_service.inventory_stock (
    id BIGSERIAL PRIMARY KEY,
    asset_definition_id BIGINT NOT NULL REFERENCES inventory_service.asset_definitions(id),
    stock_location_id BIGINT NOT NULL REFERENCES inventory_service.stock_locations(id),
    quantity_total INTEGER NOT NULL DEFAULT 0,
    quantity_available INTEGER NOT NULL DEFAULT 0,
    quantity_reserved INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_stock_qty CHECK (
        quantity_available >= 0
        AND quantity_reserved >= 0
        AND quantity_total >= 0
        AND quantity_available + quantity_reserved <= quantity_total
    ),
    UNIQUE(asset_definition_id, stock_location_id)
);

-- asset_requests table (schema for P4)
CREATE TABLE IF NOT EXISTS inventory_service.asset_requests (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(255) UNIQUE,
    requester_user_id VARCHAR(100) NOT NULL,
    requester_name VARCHAR(200) NOT NULL,
    requester_email VARCHAR(150) NOT NULL,
    requester_phone VARCHAR(20),
    department_code VARCHAR(50),
    office_location VARCHAR(50) NOT NULL,
    asset_definition_id BIGINT REFERENCES inventory_service.asset_definitions(id),
    asset_category_id BIGINT REFERENCES inventory_service.asset_categories(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    procurement_required BOOLEAN NOT NULL DEFAULT FALSE,
    approx_price DECIMAL(10, 2),
    delivery_date DATE,
    justification VARCHAR(2000),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approved_by_user_id VARCHAR(100),
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_asset_req_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'FULFILLED', 'PROCUREMENT_INITIATED', 'CANCELLED')
    ),
    CONSTRAINT chk_asset_req_qty CHECK (quantity > 0)
);

-- Seed stock locations
INSERT INTO inventory_service.stock_locations (name, office_location, department_code, description) VALUES
    ('Seattle IT Storage',       'SEATTLE_US',   'IT',  'IT equipment storage, Seattle office'),
    ('Bangalore IT Storage',     'BANGALORE_IN', 'IT',  'IT equipment storage, Bangalore office'),
    ('Shillong IT Storage',      'SHILLONG_IN',  'IT',  'IT equipment storage, Shillong office'),
    ('Stockholm IT Storage',     'STOCKHOLM_SE', 'IT',  'IT equipment storage, Stockholm office'),
    ('Melbourne IT Storage',     'MELBOURNE_AU', 'IT',  'IT equipment storage, Melbourne office'),
    ('Seattle Office Storage',   'SEATTLE_US',   'OPS', 'General office assets, Seattle'),
    ('Bangalore Office Storage', 'BANGALORE_IN', 'OPS', 'General office assets, Bangalore')
ON CONFLICT DO NOTHING;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_stock_loc_office ON inventory_service.stock_locations(office_location);
CREATE INDEX IF NOT EXISTS idx_stock_loc_dept ON inventory_service.stock_locations(department_code);
CREATE INDEX IF NOT EXISTS idx_inv_stock_def ON inventory_service.inventory_stock(asset_definition_id);
CREATE INDEX IF NOT EXISTS idx_inv_stock_loc ON inventory_service.inventory_stock(stock_location_id);
CREATE INDEX IF NOT EXISTS idx_asset_req_status ON inventory_service.asset_requests(status);
CREATE INDEX IF NOT EXISTS idx_asset_req_user ON inventory_service.asset_requests(requester_user_id);
