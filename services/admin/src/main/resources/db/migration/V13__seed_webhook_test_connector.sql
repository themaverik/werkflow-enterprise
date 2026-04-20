-- Seeds a webhook test connector pointing to httpbin.org for the default tenant.
-- Useful for testing connector-based service tasks without a real backend.

INSERT INTO tenant_service_endpoints (tenant_code, service_key, connector_key, display_name, base_url, environment, active, created_at, updated_at)
VALUES (
    'default',
    'webhook-test',
    'webhook-test',
    'Webhook Test (httpbin.org)',
    'https://httpbin.org',
    'development',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT ON CONSTRAINT uq_tse_tenant_connector_env DO NOTHING;
