-- M9: Connector Runtime — schema changes
-- 1. connector_type on tenant_service_endpoints
-- 2. secret_value replaces secret_ref on tenant_api_credentials
-- 3. NONE added to auth_scheme check
-- 4. new tenant_connector_paths table

-- 1. Add connector_type to tenant_service_endpoints
ALTER TABLE admin_service.tenant_service_endpoints
    ADD COLUMN connector_type VARCHAR(20) NOT NULL DEFAULT 'API'
        CONSTRAINT chk_connector_type CHECK (connector_type IN ('API', 'WEBHOOK', 'MCP', 'OTHER'));

-- 2. Add secret_value, make secret_ref nullable
ALTER TABLE admin_service.tenant_api_credentials
    ADD COLUMN secret_value TEXT;

ALTER TABLE admin_service.tenant_api_credentials
    ALTER COLUMN secret_ref DROP NOT NULL;

-- 3. Add NONE to auth_scheme allowed values
ALTER TABLE admin_service.tenant_api_credentials
    DROP CONSTRAINT chk_auth_scheme;

ALTER TABLE admin_service.tenant_api_credentials
    ADD CONSTRAINT chk_auth_scheme
        CHECK (auth_scheme IN ('API_KEY', 'BEARER', 'BASIC', 'OAUTH2_CLIENT_CREDENTIALS', 'NONE'));

-- 4. tenant_connector_paths
CREATE TABLE admin_service.tenant_connector_paths (
    id               BIGSERIAL    PRIMARY KEY,
    connector_key    VARCHAR(100) NOT NULL,
    tenant_code      VARCHAR(50)  NOT NULL,
    path             VARCHAR(500) NOT NULL,
    http_method      VARCHAR(10)  NOT NULL
                         CONSTRAINT chk_tcp_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    interaction_type VARCHAR(20)  NOT NULL
                         CONSTRAINT chk_tcp_interaction CHECK (interaction_type IN ('QUERY', 'ACTION', 'WEBHOOK_OUT')),
    description      VARCHAR(500),
    request_schema   JSONB,
    response_schema  JSONB,
    variable_mappings JSONB,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tcp_connector ON admin_service.tenant_connector_paths (connector_key, tenant_code);
CREATE UNIQUE INDEX uq_tcp_path ON admin_service.tenant_connector_paths (connector_key, tenant_code, path, http_method);
