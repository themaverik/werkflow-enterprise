-- Budget Plans (department budgets for fiscal periods)
CREATE TABLE IF NOT EXISTS budget_plans (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL, -- FK to admin_service.departments
    fiscal_year INTEGER NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    allocated_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    spent_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    approved_by_user_id BIGINT,
    approved_date TIMESTAMP,
    notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_budget_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ACTIVE', 'CLOSED')),
    CONSTRAINT chk_budget_amounts CHECK (total_amount >= 0 AND allocated_amount >= 0 AND spent_amount >= 0)
);

-- Budget Categories (expense categories for budgeting)
CREATE TABLE IF NOT EXISTS budget_categories (
    id BIGSERIAL PRIMARY KEY,
    parent_category_id BIGINT REFERENCES budget_categories(id),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Budget Line Items
CREATE TABLE IF NOT EXISTS budget_line_items (
    id BIGSERIAL PRIMARY KEY,
    budget_plan_id BIGINT NOT NULL REFERENCES budget_plans(id),
    category_id BIGINT NOT NULL REFERENCES budget_categories(id),
    description VARCHAR(500) NOT NULL,
    allocated_amount DECIMAL(15, 2) NOT NULL,
    spent_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_line_item_amounts CHECK (allocated_amount >= 0 AND spent_amount >= 0)
);

-- Expenses
CREATE TABLE IF NOT EXISTS expenses (
    id BIGSERIAL PRIMARY KEY,
    budget_line_item_id BIGINT REFERENCES budget_line_items(id),
    department_id BIGINT NOT NULL,
    expense_date DATE NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    category_id BIGINT NOT NULL REFERENCES budget_categories(id),
    vendor_name VARCHAR(200),
    description VARCHAR(1000) NOT NULL,
    receipt_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    submitted_by_user_id BIGINT NOT NULL,
    approved_by_user_id BIGINT,
    approved_date TIMESTAMP,
    process_instance_id VARCHAR(255), -- Flowable workflow
    rejection_reason VARCHAR(1000),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_expense_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED')),
    CONSTRAINT chk_expense_amount CHECK (amount > 0)
);

-- Approval Thresholds (amount-based approval rules)
CREATE TABLE IF NOT EXISTS approval_thresholds (
    id BIGSERIAL PRIMARY KEY,
    min_amount DECIMAL(15, 2) NOT NULL,
    max_amount DECIMAL(15, 2),
    approver_role VARCHAR(100) NOT NULL,
    department_id BIGINT, -- null = applies to all departments
    approval_order INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_threshold_amounts CHECK (min_amount >= 0 AND (max_amount IS NULL OR max_amount > min_amount))
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_budget_plan_dept ON budget_plans(department_id);
CREATE INDEX IF NOT EXISTS idx_budget_plan_fiscal ON budget_plans(fiscal_year);
CREATE INDEX IF NOT EXISTS idx_budget_plan_status ON budget_plans(status);
CREATE INDEX IF NOT EXISTS idx_budget_line_plan ON budget_line_items(budget_plan_id);
CREATE INDEX IF NOT EXISTS idx_budget_line_category ON budget_line_items(category_id);
CREATE INDEX IF NOT EXISTS idx_expense_dept ON expenses(department_id);
CREATE INDEX IF NOT EXISTS idx_expense_date ON expenses(expense_date);
CREATE INDEX IF NOT EXISTS idx_expense_status ON expenses(status);
CREATE INDEX IF NOT EXISTS idx_expense_line_item ON expenses(budget_line_item_id);
CREATE INDEX IF NOT EXISTS idx_expense_submitter ON expenses(submitted_by_user_id);
