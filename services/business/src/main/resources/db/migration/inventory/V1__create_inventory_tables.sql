-- Asset Categories (hierarchical)
CREATE TABLE IF NOT EXISTS asset_categories (
    id BIGSERIAL PRIMARY KEY,
    parent_category_id BIGINT REFERENCES asset_categories(id),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    description VARCHAR(1000),
    primary_custodian_dept_id BIGINT NOT NULL, -- FK to admin_service.departments
    requires_approval BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asset Definitions (catalog)
CREATE TABLE IF NOT EXISTS asset_definitions (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES asset_categories(id),
    sku VARCHAR(100) UNIQUE,
    name VARCHAR(200) NOT NULL,
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    specifications JSONB,
    unit_cost DECIMAL(10, 2),
    expected_lifespan_months INTEGER,
    requires_maintenance BOOLEAN NOT NULL DEFAULT false,
    maintenance_interval_months INTEGER,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asset Instances (physical items)
CREATE TABLE IF NOT EXISTS asset_instances (
    id BIGSERIAL PRIMARY KEY,
    asset_definition_id BIGINT NOT NULL REFERENCES asset_definitions(id),
    asset_tag VARCHAR(100) UNIQUE NOT NULL,
    serial_number VARCHAR(100),
    purchase_date DATE,
    purchase_cost DECIMAL(10, 2),
    warranty_expiry_date DATE,
    condition VARCHAR(50) DEFAULT 'NEW',
    status VARCHAR(50) DEFAULT 'AVAILABLE',
    current_location VARCHAR(200),
    notes VARCHAR(2000),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_condition CHECK (condition IN ('NEW', 'GOOD', 'FAIR', 'POOR', 'DAMAGED', 'NEEDS_REPAIR')),
    CONSTRAINT chk_status CHECK (status IN ('AVAILABLE', 'IN_USE', 'MAINTENANCE', 'RETIRED', 'DISPOSED', 'LOST'))
);

-- Custody Records (who has what)
CREATE TABLE IF NOT EXISTS custody_records (
    id BIGSERIAL PRIMARY KEY,
    asset_instance_id BIGINT NOT NULL REFERENCES asset_instances(id),
    custodian_dept_id BIGINT NOT NULL, -- FK to admin_service.departments
    custodian_user_id BIGINT, -- FK to admin_service.users (optional)
    physical_location VARCHAR(200),
    custody_type VARCHAR(50) NOT NULL DEFAULT 'PERMANENT',
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP, -- null = current custody
    assigned_by_user_id BIGINT, -- FK to admin_service.users
    return_condition VARCHAR(50),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_custody_type CHECK (custody_type IN ('PERMANENT', 'TEMPORARY', 'LOAN'))
);

-- Transfer Requests (workflow-driven)
CREATE TABLE IF NOT EXISTS transfer_requests (
    id BIGSERIAL PRIMARY KEY,
    asset_instance_id BIGINT NOT NULL REFERENCES asset_instances(id),
    from_dept_id BIGINT NOT NULL,
    from_user_id BIGINT,
    to_dept_id BIGINT NOT NULL,
    to_user_id BIGINT,
    transfer_type VARCHAR(50) NOT NULL,
    transfer_reason VARCHAR(1000) NOT NULL,
    expected_return_date DATE,
    initiated_by_user_id BIGINT NOT NULL,
    initiated_date TIMESTAMP NOT NULL,
    approved_by_user_id BIGINT,
    approved_date TIMESTAMP,
    completed_date TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    process_instance_id VARCHAR(255),
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transfer_type CHECK (transfer_type IN ('INTER_DEPARTMENT', 'RETURN_TO_OWNER', 'DISPOSAL', 'LOAN')),
    CONSTRAINT chk_transfer_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'))
);

-- Maintenance Records
CREATE TABLE IF NOT EXISTS maintenance_records (
    id BIGSERIAL PRIMARY KEY,
    asset_instance_id BIGINT NOT NULL REFERENCES asset_instances(id),
    maintenance_type VARCHAR(50) NOT NULL,
    scheduled_date DATE,
    completed_date DATE,
    performed_by VARCHAR(200),
    cost DECIMAL(10, 2),
    description VARCHAR(2000),
    next_maintenance_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_maintenance_type CHECK (maintenance_type IN ('SCHEDULED', 'REPAIR', 'INSPECTION', 'CALIBRATION', 'UPGRADE'))
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_asset_def_category ON asset_definitions(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_inst_definition ON asset_instances(asset_definition_id);
CREATE INDEX IF NOT EXISTS idx_asset_inst_status ON asset_instances(status);
CREATE INDEX IF NOT EXISTS idx_custody_asset ON custody_records(asset_instance_id);
CREATE INDEX IF NOT EXISTS idx_custody_dept ON custody_records(custodian_dept_id);
CREATE INDEX IF NOT EXISTS idx_custody_user ON custody_records(custodian_user_id);
CREATE INDEX IF NOT EXISTS idx_custody_current ON custody_records(asset_instance_id, end_date) WHERE end_date IS NULL;
CREATE INDEX IF NOT EXISTS idx_transfer_asset ON transfer_requests(asset_instance_id);
CREATE INDEX IF NOT EXISTS idx_transfer_status ON transfer_requests(status);
CREATE INDEX IF NOT EXISTS idx_transfer_from_dept ON transfer_requests(from_dept_id);
CREATE INDEX IF NOT EXISTS idx_transfer_to_dept ON transfer_requests(to_dept_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_asset ON maintenance_records(asset_instance_id);
