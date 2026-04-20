-- Flyway Migration V1: Create Procurement Service Tables
-- Matches entity definitions in com.werkflow.business.procurement.entity

-- =============================================================================
-- Vendors
-- =============================================================================
CREATE TABLE vendors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) UNIQUE,
    contact_person VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(500),
    tax_id VARCHAR(50),
    payment_terms VARCHAR(100),
    rating NUMERIC(3, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(2000),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_vendor_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLACKLISTED'))
);

CREATE INDEX idx_vendor_code ON vendors(code);
CREATE INDEX idx_vendor_status ON vendors(status);

-- =============================================================================
-- Purchase Requests
-- =============================================================================
CREATE TABLE purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    pr_number VARCHAR(50) NOT NULL UNIQUE,
    requesting_dept_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    request_date DATE NOT NULL,
    required_by_date DATE,
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    justification VARCHAR(2000) NOT NULL,
    notes VARCHAR(1000),
    total_amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approved_by_user_id BIGINT,
    approved_date TIMESTAMP,
    process_instance_id VARCHAR(255),
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pr_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_pr_status CHECK (status IN ('DRAFT', 'PENDING', 'SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ORDERED', 'RECEIVED', 'CANCELLED'))
);

CREATE INDEX idx_pr_number ON purchase_requests(pr_number);
CREATE INDEX idx_pr_status ON purchase_requests(status);
CREATE INDEX idx_pr_requester ON purchase_requests(requester_user_id);

-- =============================================================================
-- PR Line Items
-- =============================================================================
CREATE TABLE pr_line_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_request_id BIGINT NOT NULL,
    line_number INTEGER NOT NULL,
    asset_definition_id BIGINT,
    item_description VARCHAR(500) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_of_measure VARCHAR(50),
    estimated_unit_price NUMERIC(15, 2) NOT NULL,
    estimated_total_amount NUMERIC(15, 2) NOT NULL,
    total_price NUMERIC(15, 2) NOT NULL,
    budget_category_id BIGINT,
    specifications JSONB,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_pr_line_item_pr FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id) ON DELETE CASCADE
);

CREATE INDEX idx_pr_line_item_pr_id ON pr_line_items(purchase_request_id);

-- =============================================================================
-- Purchase Orders
-- =============================================================================
CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_request_id BIGINT,
    vendor_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    actual_delivery_date DATE,
    total_amount NUMERIC(15, 2) NOT NULL,
    tax_amount NUMERIC(15, 2),
    shipping_amount NUMERIC(15, 2),
    grand_total NUMERIC(15, 2) NOT NULL,
    payment_terms VARCHAR(100),
    delivery_address VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(2000),
    process_instance_id VARCHAR(255),
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_po_status CHECK (status IN ('DRAFT', 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT fk_po_pr FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_po_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE RESTRICT
);

CREATE INDEX idx_po_number ON purchase_orders(po_number);
CREATE INDEX idx_po_status ON purchase_orders(status);
CREATE INDEX idx_po_vendor_id ON purchase_orders(vendor_id);

-- =============================================================================
-- PO Line Items
-- =============================================================================
CREATE TABLE po_line_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    pr_line_item_id BIGINT,
    line_number INTEGER NOT NULL,
    item_description VARCHAR(500) NOT NULL,
    description VARCHAR(500) NOT NULL,
    ordered_quantity INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_of_measure VARCHAR(50),
    unit_price NUMERIC(15, 2) NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL,
    total_price NUMERIC(15, 2) NOT NULL,
    specifications JSONB,
    notes VARCHAR(500),
    received_quantity INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_po_line_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_po_line_pr_line FOREIGN KEY (pr_line_item_id) REFERENCES pr_line_items(id) ON DELETE SET NULL
);

CREATE INDEX idx_po_line_po_id ON po_line_items(purchase_order_id);

-- =============================================================================
-- Receipts
-- =============================================================================
CREATE TABLE receipts (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    received_by_user_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(2000),
    discrepancy_notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_receipt_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_receipt_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'RECEIVED', 'DISCREPANCY', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_receipt_po_id ON receipts(purchase_order_id);
CREATE INDEX idx_receipt_number ON receipts(receipt_number);

-- =============================================================================
-- Receipt Line Items
-- =============================================================================
CREATE TABLE receipt_line_items (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    po_line_item_id BIGINT NOT NULL,
    quantity_received INTEGER NOT NULL,
    accepted_quantity INTEGER NOT NULL DEFAULT 0,
    rejected_quantity INTEGER DEFAULT 0,
    condition VARCHAR(50) NOT NULL DEFAULT 'GOOD',
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_receipt_line_receipt FOREIGN KEY (receipt_id) REFERENCES receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_line_po_line FOREIGN KEY (po_line_item_id) REFERENCES po_line_items(id) ON DELETE CASCADE,
    CONSTRAINT chk_item_condition CHECK (condition IN ('NEW', 'GOOD', 'ACCEPTABLE', 'DAMAGED', 'DEFECTIVE'))
);

CREATE INDEX idx_receipt_line_receipt_id ON receipt_line_items(receipt_id);
CREATE INDEX idx_receipt_line_po_line_id ON receipt_line_items(po_line_item_id);
