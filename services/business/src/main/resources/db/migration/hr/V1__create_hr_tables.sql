-- Flyway Migration V1: Create HR Management Tables
-- This script creates all tables for the HR management system

-- Create departments table
CREATE TABLE IF NOT EXISTS departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    code VARCHAR(50) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    parent_department_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_parent_department FOREIGN KEY (parent_department_id) REFERENCES departments(id) ON DELETE SET NULL
);

-- Create employees table
CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    date_of_birth DATE NOT NULL,
    join_date DATE NOT NULL,
    end_date DATE,
    job_title VARCHAR(100),
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    salary NUMERIC(15, 2) NOT NULL,
    address VARCHAR(500),
    department_id BIGINT NOT NULL,
    manager_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL,
    CONSTRAINT chk_employment_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED', 'RESIGNED'))
);

-- Create leaves table
CREATE TABLE IF NOT EXISTS leaves (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    number_of_days INTEGER NOT NULL,
    reason VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT,
    approved_at DATE,
    approval_remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_leave_approver FOREIGN KEY (approved_by) REFERENCES employees(id) ON DELETE SET NULL,
    CONSTRAINT chk_leave_type CHECK (leave_type IN ('ANNUAL', 'SICK', 'MATERNITY', 'PATERNITY', 'UNPAID', 'COMPENSATORY', 'BEREAVEMENT', 'STUDY')),
    CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_leave_dates CHECK (end_date >= start_date)
);

-- Create attendances table
CREATE TABLE IF NOT EXISTS attendances (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    check_in_time TIME,
    check_out_time TIME,
    status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    remarks VARCHAR(500),
    worked_hours DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT uk_employee_date UNIQUE (employee_id, attendance_date),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE', 'HOLIDAY', 'WEEKEND'))
);

-- Create performance_reviews table
CREATE TABLE IF NOT EXISTS performance_reviews (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    review_date DATE NOT NULL,
    review_period_start DATE NOT NULL,
    review_period_end DATE NOT NULL,
    rating VARCHAR(30) NOT NULL,
    score NUMERIC(5, 2) NOT NULL,
    strengths VARCHAR(2000),
    areas_for_improvement VARCHAR(2000),
    goals VARCHAR(2000),
    comments VARCHAR(2000),
    reviewer_id BIGINT NOT NULL,
    employee_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_review_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT chk_rating CHECK (rating IN ('OUTSTANDING', 'EXCEEDS_EXPECTATIONS', 'MEETS_EXPECTATIONS', 'NEEDS_IMPROVEMENT', 'UNSATISFACTORY')),
    CONSTRAINT chk_score CHECK (score >= 0 AND score <= 100),
    CONSTRAINT chk_review_period CHECK (review_period_end >= review_period_start)
);

-- Create payrolls table
CREATE TABLE IF NOT EXISTS payrolls (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    payment_month INTEGER NOT NULL,
    payment_year INTEGER NOT NULL,
    payment_date DATE NOT NULL,
    basic_salary NUMERIC(15, 2) NOT NULL,
    allowances NUMERIC(15, 2) DEFAULT 0,
    bonuses NUMERIC(15, 2) DEFAULT 0,
    overtime_pay NUMERIC(15, 2) DEFAULT 0,
    tax_deduction NUMERIC(15, 2) DEFAULT 0,
    insurance_deduction NUMERIC(15, 2) DEFAULT 0,
    other_deductions NUMERIC(15, 2) DEFAULT 0,
    gross_salary NUMERIC(15, 2),
    net_salary NUMERIC(15, 2),
    remarks VARCHAR(1000),
    is_paid BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_payroll_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT uk_employee_month_year UNIQUE (employee_id, payment_month, payment_year),
    CONSTRAINT chk_payment_month CHECK (payment_month >= 1 AND payment_month <= 12),
    CONSTRAINT chk_payment_year CHECK (payment_year >= 2000)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_employee_email ON employees(email);
CREATE INDEX IF NOT EXISTS idx_employee_code ON employees(employee_code);
CREATE INDEX IF NOT EXISTS idx_employee_department ON employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employee_manager ON employees(manager_id);

CREATE INDEX IF NOT EXISTS idx_leave_employee ON leaves(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_status ON leaves(status);
CREATE INDEX IF NOT EXISTS idx_leave_dates ON leaves(start_date, end_date);

CREATE INDEX IF NOT EXISTS idx_attendance_employee ON attendances(employee_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendances(attendance_date);

CREATE INDEX IF NOT EXISTS idx_review_employee ON performance_reviews(employee_id);
CREATE INDEX IF NOT EXISTS idx_review_date ON performance_reviews(review_date);
CREATE INDEX IF NOT EXISTS idx_review_reviewer ON performance_reviews(reviewer_id);

CREATE INDEX IF NOT EXISTS idx_payroll_employee ON payrolls(employee_id);
CREATE INDEX IF NOT EXISTS idx_payroll_date ON payrolls(payment_date);
CREATE INDEX IF NOT EXISTS idx_payroll_period ON payrolls(payment_year, payment_month);

-- Add comments to tables for documentation
COMMENT ON TABLE departments IS 'Organizational departments';
COMMENT ON TABLE employees IS 'Employee information';
COMMENT ON TABLE leaves IS 'Employee leave requests and approvals';
COMMENT ON TABLE attendances IS 'Daily attendance records';
COMMENT ON TABLE performance_reviews IS 'Employee performance evaluations';
COMMENT ON TABLE payrolls IS 'Employee salary and payment records';
