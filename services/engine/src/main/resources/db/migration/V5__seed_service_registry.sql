-- =====================================================
-- Werkflow Service Registry Seed Data
-- Migration V5: Seed Service Registry with Initial Services
-- Purpose: Initialize service registry with platform services
-- Author: Backend Team
-- Created: 2025-11-24
-- =====================================================

-- =====================================================
-- 1. INSERT SERVICES INTO service_registry
-- =====================================================

-- HR Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    version,
    is_active,
    health_status,
    metadata,
    created_by
) VALUES (
    'hr-service',
    'Human Resources Service',
    'Manages employee data, leave requests, payroll, and HR workflows',
    'BUSINESS',
    '1.0.0',
    true,
    'UNKNOWN',
    '{"department": "HR", "owner": "hr-team@werkflow.com", "cost_center": "CC-1001"}'::jsonb,
    'system'
);

-- Finance Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    version,
    is_active,
    health_status,
    metadata,
    created_by
) VALUES (
    'finance-service',
    'Finance & Accounting Service',
    'Handles invoicing, payments, expense tracking, and financial reporting',
    'BUSINESS',
    '1.0.0',
    true,
    'UNKNOWN',
    '{"department": "Finance", "owner": "finance-team@werkflow.com", "cost_center": "CC-2001"}'::jsonb,
    'system'
);

-- Procurement Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    version,
    is_active,
    health_status,
    metadata,
    created_by
) VALUES (
    'procurement-service',
    'Procurement Service',
    'Manages purchase orders, vendor management, and procurement workflows',
    'BUSINESS',
    '1.0.0',
    true,
    'UNKNOWN',
    '{"department": "Operations", "owner": "procurement-team@werkflow.com", "cost_center": "CC-3001"}'::jsonb,
    'system'
);

-- Inventory Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    version,
    is_active,
    health_status,
    metadata,
    created_by
) VALUES (
    'inventory-service',
    'Inventory Management Service',
    'Tracks inventory levels, stock movements, and warehouse operations',
    'BUSINESS',
    '1.0.0',
    true,
    'UNKNOWN',
    '{"department": "Operations", "owner": "inventory-team@werkflow.com", "cost_center": "CC-3002"}'::jsonb,
    'system'
);

-- Admin Service
INSERT INTO service_registry (
    service_name,
    display_name,
    description,
    service_type,
    version,
    is_active,
    health_status,
    metadata,
    created_by
) VALUES (
    'admin-service',
    'Administration Service',
    'Provides system configuration, user management, and administrative functions',
    'TECHNICAL',
    '1.0.0',
    true,
    'UNKNOWN',
    '{"department": "IT", "owner": "platform-team@werkflow.com", "cost_center": "CC-4001"}'::jsonb,
    'system'
);

-- =====================================================
-- 2. INSERT ENVIRONMENT URLs
-- =====================================================

-- HR Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8082', true, 100, true, 'http://localhost:8082/actuator/health', 60
FROM service_registry WHERE service_name = 'hr-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://hr-service:8082', true, 100, true, 'http://hr-service:8082/actuator/health', 60
FROM service_registry WHERE service_name = 'hr-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://hr-api.werkflow.com', true, 100, true, 'https://hr-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'hr-service';

-- Finance Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8084', true, 100, true, 'http://localhost:8084/actuator/health', 60
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://finance-service:8084', true, 100, true, 'http://finance-service:8084/actuator/health', 60
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://finance-api.werkflow.com', true, 100, true, 'https://finance-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'finance-service';

-- Procurement Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8085', true, 100, true, 'http://localhost:8085/actuator/health', 60
FROM service_registry WHERE service_name = 'procurement-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://procurement-service:8085', true, 100, true, 'http://procurement-service:8085/actuator/health', 60
FROM service_registry WHERE service_name = 'procurement-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://procurement-api.werkflow.com', true, 100, true, 'https://procurement-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'procurement-service';

-- Inventory Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8086', true, 100, true, 'http://localhost:8086/actuator/health', 60
FROM service_registry WHERE service_name = 'inventory-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://inventory-service:8086', true, 100, true, 'http://inventory-service:8086/actuator/health', 60
FROM service_registry WHERE service_name = 'inventory-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://inventory-api.werkflow.com', true, 100, true, 'https://inventory-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'inventory-service';

-- Admin Service URLs
INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'DEV', 'http://localhost:8083', true, 100, true, 'http://localhost:8083/actuator/health', 60
FROM service_registry WHERE service_name = 'admin-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'STAGING', 'http://admin-service:8083', true, 100, true, 'http://admin-service:8083/actuator/health', 60
FROM service_registry WHERE service_name = 'admin-service';

INSERT INTO service_environment_urls (service_id, environment, base_url, is_default, priority, is_active, health_check_url, health_check_interval_seconds)
SELECT id, 'PROD', 'https://admin-api.werkflow.com', true, 100, true, 'https://admin-api.werkflow.com/actuator/health', 30
FROM service_registry WHERE service_name = 'admin-service';

-- =====================================================
-- 3. INSERT EXAMPLE ENDPOINTS (Finance Service)
-- =====================================================

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/invoices',
    'GET',
    'List all invoices',
    true,
    false,
    30000,
    3,
    true
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/invoices',
    'POST',
    'Create new invoice',
    true,
    false,
    30000,
    3,
    true
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/invoices/{id}',
    'GET',
    'Get invoice by ID',
    true,
    false,
    30000,
    3,
    true
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/invoices/{id}',
    'PUT',
    'Update invoice',
    true,
    false,
    30000,
    3,
    true
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/invoices/{id}/approve',
    'POST',
    'Approve invoice',
    true,
    false,
    30000,
    3,
    true
FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_endpoints (service_id, endpoint_path, http_method, description, requires_auth, is_public, timeout_ms, retry_count, circuit_breaker_enabled)
SELECT
    id,
    '/api/payments',
    'POST',
    'Process payment',
    true,
    false,
    60000,
    2,
    true
FROM service_registry WHERE service_name = 'finance-service';

-- =====================================================
-- 4. INSERT SERVICE TAGS
-- =====================================================

-- HR Service Tags
INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'department', 'HR' FROM service_registry WHERE service_name = 'hr-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'team', 'hr-platform' FROM service_registry WHERE service_name = 'hr-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'priority', 'high' FROM service_registry WHERE service_name = 'hr-service';

-- Finance Service Tags
INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'department', 'Finance' FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'team', 'finance-platform' FROM service_registry WHERE service_name = 'finance-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'priority', 'critical' FROM service_registry WHERE service_name = 'finance-service';

-- Procurement Service Tags
INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'department', 'Operations' FROM service_registry WHERE service_name = 'procurement-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'team', 'procurement-platform' FROM service_registry WHERE service_name = 'procurement-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'priority', 'medium' FROM service_registry WHERE service_name = 'procurement-service';

-- Inventory Service Tags
INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'department', 'Operations' FROM service_registry WHERE service_name = 'inventory-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'team', 'inventory-platform' FROM service_registry WHERE service_name = 'inventory-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'priority', 'medium' FROM service_registry WHERE service_name = 'inventory-service';

-- Admin Service Tags
INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'department', 'IT' FROM service_registry WHERE service_name = 'admin-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'team', 'platform-team' FROM service_registry WHERE service_name = 'admin-service';

INSERT INTO service_tags (service_id, tag_name, tag_value)
SELECT id, 'priority', 'high' FROM service_registry WHERE service_name = 'admin-service';
