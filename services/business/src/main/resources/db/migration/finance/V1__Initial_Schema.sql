-- Flyway Migration V1: Create Finance Service Initial Schema
-- This script creates all tables for the Finance management system

-- Create CapEx Requests table
CREATE TABLE IF NOT EXISTS capex_requests (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    approval_level VARCHAR(30) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    request_date DATE NOT NULL,
    expected_completion_date DATE,
    business_justification VARCHAR(500),
    expected_benefits VARCHAR(500),
    budget_year INTEGER,
    department_name VARCHAR(100),
    approved_amount NUMERIC(15, 2),
    approved_by VARCHAR(255),
    approved_at DATE,
    rejection_reason VARCHAR(500),
    rejected_by VARCHAR(255),
    rejected_at DATE,
    workflow_instance_id VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT chk_capex_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'BUDGETED', 'IN_PROCUREMENT', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_capex_category CHECK (category IN ('INFRASTRUCTURE', 'INFORMATION_TECHNOLOGY', 'MACHINERY_EQUIPMENT', 'VEHICLES', 'FURNITURE_FIXTURES', 'SOFTWARE_LICENSES', 'MAINTENANCE_RENOVATION', 'RESEARCH_DEVELOPMENT', 'OTHER')),
    CONSTRAINT chk_capex_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_capex_approval_level CHECK (approval_level IN ('DEPARTMENT_HEAD', 'FINANCE_MANAGER', 'CFO', 'CEO', 'BOARD_EXECUTIVE'))
);

-- Create indexes for CapEx Requests
CREATE INDEX IF NOT EXISTS idx_capex_request_number ON capex_requests(request_number);
CREATE INDEX IF NOT EXISTS idx_capex_status ON capex_requests(status);
CREATE INDEX IF NOT EXISTS idx_capex_category ON capex_requests(category);
CREATE INDEX IF NOT EXISTS idx_capex_requested_by ON capex_requests(requested_by);
CREATE INDEX IF NOT EXISTS idx_capex_department ON capex_requests(department_name);
CREATE INDEX IF NOT EXISTS idx_capex_budget_year ON capex_requests(budget_year);

-- Create CapEx Approvals table
CREATE TABLE IF NOT EXISTS capex_approvals (
    id BIGSERIAL PRIMARY KEY,
    capex_request_id BIGINT NOT NULL,
    approval_level VARCHAR(30) NOT NULL,
    approver VARCHAR(255) NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remarks VARCHAR(500),
    approved_at DATE,
    approval_order INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_capex_approval_request FOREIGN KEY (capex_request_id) REFERENCES capex_requests(id) ON DELETE CASCADE,
    CONSTRAINT chk_capex_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_capex_approval_level CHECK (approval_level IN ('DEPARTMENT_HEAD', 'FINANCE_MANAGER', 'CFO', 'CEO', 'BOARD_EXECUTIVE'))
);

-- Create indexes for CapEx Approvals
CREATE INDEX IF NOT EXISTS idx_capex_approval_request_id ON capex_approvals(capex_request_id);
CREATE INDEX IF NOT EXISTS idx_capex_approval_level ON capex_approvals(approval_level);
CREATE INDEX IF NOT EXISTS idx_capex_approval_approver ON capex_approvals(approver);
CREATE INDEX IF NOT EXISTS idx_capex_approval_status ON capex_approvals(approval_status);

-- Create Budgets table
CREATE TABLE IF NOT EXISTS budgets (
    id BIGSERIAL PRIMARY KEY,
    budget_year INTEGER NOT NULL,
    category VARCHAR(30) NOT NULL,
    department_name VARCHAR(100),
    allocated_amount NUMERIC(15, 2) NOT NULL,
    utilized_amount NUMERIC(15, 2) DEFAULT 0,
    remaining_amount NUMERIC(15, 2),
    remarks VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT chk_budget_category CHECK (category IN ('INFRASTRUCTURE', 'INFORMATION_TECHNOLOGY', 'MACHINERY_EQUIPMENT', 'VEHICLES', 'FURNITURE_FIXTURES', 'SOFTWARE_LICENSES', 'MAINTENANCE_RENOVATION', 'RESEARCH_DEVELOPMENT', 'OTHER')),
    CONSTRAINT uk_budget_year_category_dept UNIQUE (budget_year, category, department_name)
);

-- Create indexes for Budgets
CREATE INDEX IF NOT EXISTS idx_budget_year ON budgets(budget_year);
CREATE INDEX IF NOT EXISTS idx_budget_category ON budgets(category);
CREATE INDEX IF NOT EXISTS idx_budget_department ON budgets(department_name);
CREATE INDEX IF NOT EXISTS idx_budget_active ON budgets(is_active);
