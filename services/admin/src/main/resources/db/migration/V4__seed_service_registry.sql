-- ================================================
-- Service Registry Seed Data Migration
-- Version: V4
-- Description: Seeds initial services and endpoints for Werkflow platform
-- Author: Werkflow Platform Team
-- Date: 2025-11-24
-- ================================================

-- ================================================
-- Insert Core Platform Services
-- ================================================

-- 1. HR Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    base_path,
    version,
    health_check_url,
    health_status,
    active
) VALUES (
    'hr-service',
    'Human Resources Service',
    'Employee management, leave requests, payroll, and performance reviews',
    'INTERNAL',
    '/api/v1',
    '1.0.0',
    '/actuator/health',
    'UNKNOWN',
    true
) ON CONFLICT (service_name) DO NOTHING;

-- 2. Finance Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    base_path,
    version,
    health_check_url,
    health_status,
    active
) VALUES (
    'finance-service',
    'Finance & Accounting Service',
    'Budget management, expense tracking, invoicing, and financial reporting',
    'INTERNAL',
    '/api/v1',
    '1.0.0',
    '/actuator/health',
    'UNKNOWN',
    true
) ON CONFLICT (service_name) DO NOTHING;

-- 3. Procurement Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    base_path,
    version,
    health_check_url,
    health_status,
    active
) VALUES (
    'procurement-service',
    'Procurement & Purchasing Service',
    'Purchase orders, vendor management, and procurement workflows',
    'INTERNAL',
    '/api/v1',
    '1.0.0',
    '/actuator/health',
    'UNKNOWN',
    true
) ON CONFLICT (service_name) DO NOTHING;

-- 4. Inventory Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    base_path,
    version,
    health_check_url,
    health_status,
    active
) VALUES (
    'inventory-service',
    'Inventory Management Service',
    'Stock management, warehouse operations, and inventory tracking',
    'INTERNAL',
    '/api/v1',
    '1.0.0',
    '/actuator/health',
    'UNKNOWN',
    true
) ON CONFLICT (service_name) DO NOTHING;

-- 5. Admin Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    base_path,
    version,
    health_check_url,
    health_status,
    active
) VALUES (
    'admin-service',
    'Administration Service',
    'User management, organization setup, roles, and permissions',
    'INTERNAL',
    '/api',
    '1.0.0',
    '/actuator/health',
    'UNKNOWN',
    true
) ON CONFLICT (service_name) DO NOTHING;

-- ================================================
-- Insert Environment URLs (Development)
-- ================================================

-- HR Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'development', 'http://localhost:8082', 1, true
FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'staging', 'http://hr-service-staging:8082', 1, true
FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'production', 'http://hr-service:8082', 1, true
FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'local', 'http://localhost:8082', 1, true
FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

-- Finance Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'development', 'http://localhost:8084', 1, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'staging', 'http://finance-service-staging:8084', 1, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'production', 'http://finance-service:8084', 1, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'local', 'http://localhost:8084', 1, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

-- Procurement Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'development', 'http://localhost:8085', 1, true
FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'staging', 'http://procurement-service-staging:8085', 1, true
FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'production', 'http://procurement-service:8085', 1, true
FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'local', 'http://localhost:8085', 1, true
FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

-- Inventory Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'development', 'http://localhost:8086', 1, true
FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'staging', 'http://inventory-service-staging:8086', 1, true
FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'production', 'http://inventory-service:8086', 1, true
FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'local', 'http://localhost:8086', 1, true
FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

-- Admin Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'development', 'http://localhost:8083', 1, true
FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'staging', 'http://admin-service-staging:8083', 1, true
FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'production', 'http://admin-service:8083', 1, true
FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

INSERT INTO service_environment_urls (service_id, environment, base_url, priority, active)
SELECT id, 'local', 'http://localhost:8083', 1, true
FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, environment, priority) DO NOTHING;

-- ================================================
-- Insert Example Endpoints for Finance Service
-- ================================================

-- Budget Endpoints
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/budgets', 'GET', 'Retrieve list of budgets', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/budgets', 'POST', 'Create new budget', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/budgets/{id}', 'GET', 'Get budget by ID', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/budgets/{id}/approve', 'POST', 'Approve budget', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

-- Expense Endpoints
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/expenses', 'GET', 'Retrieve list of expenses', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/expenses', 'POST', 'Submit new expense', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/expenses/{id}/approve', 'POST', 'Approve expense', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

-- Invoice Endpoints
INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/invoices', 'POST', 'Create invoice', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, timeout_seconds, retry_count, active)
SELECT id, '/invoices/{id}', 'GET', 'Get invoice by ID', true, 30, 0, true
FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, endpoint_path, http_method) DO NOTHING;

-- ================================================
-- Insert Service Tags for Categorization
-- ================================================

-- HR Service Tags
INSERT INTO service_tags (service_id, tag)
SELECT id, 'hr' FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'employee-management' FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'payroll' FROM service_registry WHERE service_name = 'hr-service'
ON CONFLICT (service_id, tag) DO NOTHING;

-- Finance Service Tags
INSERT INTO service_tags (service_id, tag)
SELECT id, 'finance' FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'accounting' FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'budget' FROM service_registry WHERE service_name = 'finance-service'
ON CONFLICT (service_id, tag) DO NOTHING;

-- Procurement Service Tags
INSERT INTO service_tags (service_id, tag)
SELECT id, 'procurement' FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'purchasing' FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'vendor-management' FROM service_registry WHERE service_name = 'procurement-service'
ON CONFLICT (service_id, tag) DO NOTHING;

-- Inventory Service Tags
INSERT INTO service_tags (service_id, tag)
SELECT id, 'inventory' FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'warehouse' FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'stock-management' FROM service_registry WHERE service_name = 'inventory-service'
ON CONFLICT (service_id, tag) DO NOTHING;

-- Admin Service Tags
INSERT INTO service_tags (service_id, tag)
SELECT id, 'admin' FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'user-management' FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, tag) DO NOTHING;

INSERT INTO service_tags (service_id, tag)
SELECT id, 'rbac' FROM service_registry WHERE service_name = 'admin-service'
ON CONFLICT (service_id, tag) DO NOTHING;
