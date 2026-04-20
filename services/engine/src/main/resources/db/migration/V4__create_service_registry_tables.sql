-- =====================================================
-- Werkflow Service Registry Schema
-- Migration V4: Create Service Registry Tables
-- Purpose: Dynamic service URL management and health monitoring
-- Author: Backend Team
-- Created: 2025-11-24
-- =====================================================

-- Table 1: service_registry
-- Purpose: Central registry of all microservices in the platform
CREATE TABLE service_registry (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    service_type VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    is_active BOOLEAN NOT NULL DEFAULT true,
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Add comment to service_registry table
COMMENT ON TABLE service_registry IS 'Central registry of all microservices in the Werkflow platform';
COMMENT ON COLUMN service_registry.service_name IS 'Unique internal identifier for the service (e.g., hr-service, finance-service)';
COMMENT ON COLUMN service_registry.service_type IS 'Type of service: BUSINESS, TECHNICAL, INFRASTRUCTURE';
COMMENT ON COLUMN service_registry.health_status IS 'Current health status: HEALTHY, UNHEALTHY, DEGRADED, UNKNOWN';
COMMENT ON COLUMN service_registry.metadata IS 'JSON metadata for extensibility (e.g., owner, team, cost-center)';

-- Table 2: service_endpoints
-- Purpose: API endpoints exposed by each service
CREATE TABLE service_endpoints (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    description TEXT,
    requires_auth BOOLEAN NOT NULL DEFAULT true,
    is_public BOOLEAN NOT NULL DEFAULT false,
    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    retry_count INTEGER NOT NULL DEFAULT 3,
    circuit_breaker_enabled BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_endpoints_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

-- Add comment to service_endpoints table
COMMENT ON TABLE service_endpoints IS 'API endpoints exposed by each registered service';
COMMENT ON COLUMN service_endpoints.endpoint_path IS 'Relative path of the endpoint (e.g., /api/employees)';
COMMENT ON COLUMN service_endpoints.http_method IS 'HTTP method: GET, POST, PUT, DELETE, PATCH';
COMMENT ON COLUMN service_endpoints.timeout_ms IS 'Request timeout in milliseconds';
COMMENT ON COLUMN service_endpoints.circuit_breaker_enabled IS 'Enable circuit breaker pattern for this endpoint';

-- Table 3: service_environment_urls
-- Purpose: Environment-specific URLs for each service
CREATE TABLE service_environment_urls (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT true,
    health_check_url VARCHAR(500),
    health_check_interval_seconds INTEGER NOT NULL DEFAULT 60,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_env_urls_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT uq_service_environment UNIQUE (service_id, environment)
);

-- Add comment to service_environment_urls table
COMMENT ON TABLE service_environment_urls IS 'Environment-specific base URLs for each service';
COMMENT ON COLUMN service_environment_urls.environment IS 'Environment name: DEV, STAGING, PROD';
COMMENT ON COLUMN service_environment_urls.priority IS 'Load balancing priority (lower = higher priority)';
COMMENT ON COLUMN service_environment_urls.health_check_url IS 'Full URL for health check endpoint';

-- Table 4: service_health_checks
-- Purpose: Health check history and monitoring
CREATE TABLE service_health_checks (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    status_code INTEGER,
    error_message TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    CONSTRAINT fk_service_health_checks_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

-- Add comment to service_health_checks table
COMMENT ON TABLE service_health_checks IS 'Historical health check results for monitoring and alerting';
COMMENT ON COLUMN service_health_checks.status IS 'Health check result: HEALTHY, UNHEALTHY, DEGRADED, TIMEOUT';
COMMENT ON COLUMN service_health_checks.response_time_ms IS 'Response time in milliseconds';

-- Table 5: service_tags
-- Purpose: Categorization and filtering of services
CREATE TABLE service_tags (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    tag_value VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_tags_service FOREIGN KEY (service_id)
        REFERENCES service_registry(id) ON DELETE CASCADE
);

-- Add comment to service_tags table
COMMENT ON TABLE service_tags IS 'Tags for categorizing and filtering services';
COMMENT ON COLUMN service_tags.tag_name IS 'Tag key (e.g., department, team, cost-center)';
COMMENT ON COLUMN service_tags.tag_value IS 'Tag value (e.g., HR, Engineering, CC-1001)';

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================

-- service_registry indexes
CREATE INDEX idx_service_registry_name ON service_registry(service_name);
CREATE INDEX idx_service_registry_type ON service_registry(service_type);
CREATE INDEX idx_service_registry_active ON service_registry(is_active);
CREATE INDEX idx_service_registry_health ON service_registry(health_status);

-- service_endpoints indexes
CREATE INDEX idx_service_endpoints_service_id ON service_endpoints(service_id);
CREATE INDEX idx_service_endpoints_path ON service_endpoints(endpoint_path);
CREATE INDEX idx_service_endpoints_method ON service_endpoints(http_method);
CREATE INDEX idx_service_endpoints_composite ON service_endpoints(service_id, http_method, endpoint_path);

-- service_environment_urls indexes
CREATE INDEX idx_service_env_urls_service_id ON service_environment_urls(service_id);
CREATE INDEX idx_service_env_urls_environment ON service_environment_urls(environment);
CREATE INDEX idx_service_env_urls_active ON service_environment_urls(is_active);
CREATE INDEX idx_service_env_urls_composite ON service_environment_urls(service_id, environment, is_active);

-- service_health_checks indexes
CREATE INDEX idx_service_health_checks_service_id ON service_health_checks(service_id);
CREATE INDEX idx_service_health_checks_environment ON service_health_checks(environment);
CREATE INDEX idx_service_health_checks_status ON service_health_checks(status);
CREATE INDEX idx_service_health_checks_checked_at ON service_health_checks(checked_at);
CREATE INDEX idx_service_health_checks_composite ON service_health_checks(service_id, environment, checked_at DESC);

-- service_tags indexes
CREATE INDEX idx_service_tags_service_id ON service_tags(service_id);
CREATE INDEX idx_service_tags_name ON service_tags(tag_name);
CREATE INDEX idx_service_tags_composite ON service_tags(service_id, tag_name);

-- =====================================================
-- TRIGGERS FOR UPDATED_AT TIMESTAMP
-- =====================================================

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for service_registry
CREATE TRIGGER tr_service_registry_updated_at
    BEFORE UPDATE ON service_registry
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for service_endpoints
CREATE TRIGGER tr_service_endpoints_updated_at
    BEFORE UPDATE ON service_endpoints
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for service_environment_urls
CREATE TRIGGER tr_service_env_urls_updated_at
    BEFORE UPDATE ON service_environment_urls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- GRANTS (if needed for specific database users)
-- =====================================================

-- Grant permissions to werkflow_admin (adjust as needed)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA flowable TO werkflow_admin;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA flowable TO werkflow_admin;
