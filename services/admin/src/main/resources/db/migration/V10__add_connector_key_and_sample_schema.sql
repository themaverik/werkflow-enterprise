-- V10__add_connector_key_and_sample_schema.sql
-- Add connector_key to both tables as a unified join key.
-- Back-fill from existing service_key / credential_key.
-- Add sample_schema (design-time only, nullable) and display_name to endpoints.

ALTER TABLE tenant_service_endpoints ADD COLUMN connector_key VARCHAR(100);
ALTER TABLE tenant_api_credentials   ADD COLUMN connector_key VARCHAR(100);

-- Back-fill: existing rows use their current key as the connector key
UPDATE tenant_service_endpoints SET connector_key = service_key   WHERE connector_key IS NULL;
UPDATE tenant_api_credentials   SET connector_key = credential_key WHERE connector_key IS NULL;

ALTER TABLE tenant_service_endpoints ALTER COLUMN connector_key SET NOT NULL;
ALTER TABLE tenant_api_credentials   ALTER COLUMN connector_key SET NOT NULL;

-- Unique constraint: one endpoint per connector per environment per tenant
ALTER TABLE tenant_service_endpoints
    ADD CONSTRAINT uq_tse_tenant_connector_env UNIQUE (tenant_code, connector_key, environment);

-- Unique constraint: one credential per connector per tenant
ALTER TABLE tenant_api_credentials
    ADD CONSTRAINT uq_tac_tenant_connector UNIQUE (tenant_code, connector_key);

-- sample_schema: nullable JSONB, stores parsed field list from Contract tab (design-time only)
ALTER TABLE tenant_service_endpoints ADD COLUMN sample_schema JSONB;

-- display_name: human-readable label shown in the connector registry UI
ALTER TABLE tenant_service_endpoints ADD COLUMN display_name VARCHAR(200);
UPDATE tenant_service_endpoints SET display_name = service_key WHERE display_name IS NULL;
