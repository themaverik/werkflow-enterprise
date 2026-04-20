-- ================================================
-- Service Registry Tables Migration
-- Version: V3
-- Description: Creates tables for dynamic service registry functionality
-- Author: Werkflow Platform Team
-- Date: 2025-11-24
-- ================================================

-- ================================================
-- Table: service_registry
-- Description: Master table storing all registered services
-- ================================================
CREATE TABLE IF NOT EXISTS service_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    service_type VARCHAR(50) NOT NULL CHECK (service_type IN ('INTERNAL', 'EXTERNAL', 'THIRD_PARTY')),
    base_path VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    department_id BIGINT,
    owner_user_id BIGINT,
    health_check_url VARCHAR(500),
    last_health_check_at TIMESTAMP WITH TIME ZONE,
    health_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN' CHECK (health_status IN ('HEALTHY', 'UNHEALTHY', 'UNKNOWN', 'DEGRADED')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT fk_service_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- ================================================
-- Table: service_endpoints
-- Description: Stores API endpoints for each service
-- ================================================
CREATE TABLE IF NOT EXISTS service_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    description TEXT,
    requires_auth BOOLEAN NOT NULL DEFAULT true,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    retry_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_endpoint_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT unique_service_endpoint_method UNIQUE (service_id, endpoint_path, http_method)
);

-- ================================================
-- Table: service_environment_urls
-- Description: Stores environment-specific URLs for each service
-- ================================================
CREATE TABLE IF NOT EXISTS service_environment_urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    environment VARCHAR(50) NOT NULL CHECK (environment IN ('development', 'staging', 'production', 'local')),
    base_url VARCHAR(500) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_env_url_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE,
    CONSTRAINT unique_service_environment UNIQUE (service_id, environment, priority)
);

-- ================================================
-- Table: service_health_checks
-- Description: Stores health check history for services
-- ================================================
CREATE TABLE IF NOT EXISTS service_health_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    environment VARCHAR(50) NOT NULL CHECK (environment IN ('development', 'staging', 'production', 'local')),
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK (status IN ('HEALTHY', 'UNHEALTHY', 'UNKNOWN', 'DEGRADED')),
    response_time_ms INTEGER,
    error_message TEXT,
    CONSTRAINT fk_health_check_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE
);

-- ================================================
-- Table: service_tags
-- Description: Stores tags for service categorization and filtering
-- ================================================
CREATE TABLE IF NOT EXISTS service_tags (
    service_id UUID NOT NULL,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (service_id, tag),
    CONSTRAINT fk_tag_service FOREIGN KEY (service_id) REFERENCES service_registry(id) ON DELETE CASCADE
);

-- ================================================
-- Indexes for Performance Optimization
-- ================================================

-- Index 1: Fast lookup by service name (most common query)
CREATE INDEX IF NOT EXISTS idx_service_registry_service_name ON service_registry(service_name);

-- Index 2: Fast lookup by service type for filtering
CREATE INDEX IF NOT EXISTS idx_service_registry_service_type ON service_registry(service_type);

-- Index 3: Fast lookup by department for organization-based queries
CREATE INDEX IF NOT EXISTS idx_service_registry_department_id ON service_registry(department_id);

-- Index 4: Fast lookup by owner for user-based queries
CREATE INDEX IF NOT EXISTS idx_service_registry_owner_user_id ON service_registry(owner_user_id);

-- Index 5: Fast lookup of active services only
CREATE INDEX IF NOT EXISTS idx_service_registry_active ON service_registry(active) WHERE active = true;

-- Index 6: Fast lookup of services by health status
CREATE INDEX IF NOT EXISTS idx_service_registry_health_status ON service_registry(health_status);

-- Index 7: Fast lookup of endpoints by service
CREATE INDEX IF NOT EXISTS idx_service_endpoints_service_id ON service_endpoints(service_id);

-- Index 8: Fast lookup of active endpoints for a service
CREATE INDEX IF NOT EXISTS idx_service_endpoints_service_active ON service_endpoints(service_id, active) WHERE active = true;

-- Index 9: Fast lookup of environment URLs by service and environment
CREATE INDEX IF NOT EXISTS idx_service_env_urls_service_env ON service_environment_urls(service_id, environment);

-- Index 10: Fast lookup of active URLs for service resolution
CREATE INDEX IF NOT EXISTS idx_service_env_urls_active ON service_environment_urls(service_id, environment, active) WHERE active = true;

-- Index 11: Fast lookup of recent health checks
CREATE INDEX IF NOT EXISTS idx_service_health_checks_service_time ON service_health_checks(service_id, checked_at DESC);

-- Index 12: Fast lookup of health checks by environment
CREATE INDEX IF NOT EXISTS idx_service_health_checks_env ON service_health_checks(service_id, environment, checked_at DESC);

-- Index 13: Fast tag-based service search
CREATE INDEX IF NOT EXISTS idx_service_tags_tag ON service_tags(tag);

-- ================================================
-- Comments for Documentation
-- ================================================
COMMENT ON TABLE service_registry IS 'Master registry of all services in the platform';
COMMENT ON TABLE service_endpoints IS 'API endpoints exposed by each service';
COMMENT ON TABLE service_environment_urls IS 'Environment-specific base URLs for services';
COMMENT ON TABLE service_health_checks IS 'Health check history and monitoring data';
COMMENT ON TABLE service_tags IS 'Categorization tags for services';

COMMENT ON COLUMN service_registry.service_name IS 'Unique identifier used in code (e.g., hr-service)';
COMMENT ON COLUMN service_registry.service_type IS 'Classification: INTERNAL (our services), EXTERNAL (partner services), THIRD_PARTY (external APIs)';
COMMENT ON COLUMN service_registry.base_path IS 'Base API path (e.g., /api/v1)';
COMMENT ON COLUMN service_registry.health_check_url IS 'Relative path for health checks (e.g., /actuator/health)';

COMMENT ON COLUMN service_endpoints.timeout_seconds IS 'Request timeout in seconds (default: 30)';
COMMENT ON COLUMN service_endpoints.retry_count IS 'Number of retry attempts on failure (default: 0)';

COMMENT ON COLUMN service_environment_urls.priority IS 'Priority for load balancing (lower = higher priority)';
