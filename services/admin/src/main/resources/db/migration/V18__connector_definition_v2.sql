-- M4.5: Connector Spec Formalisation
-- 1. connector_definition_v2 — stores versioned ConnectorDefinition envelopes
-- 2. design_audit_log — DTDS call audit trail
-- 3. Migrate existing tenant_service_endpoints rows into the v2 envelope format

-- -------------------------------------------------------------------------
-- 1. connector_definition_v2
-- -------------------------------------------------------------------------
CREATE TABLE admin_service.connector_definition_v2 (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key            VARCHAR(255) NOT NULL,
    version        VARCHAR(50)  NOT NULL,
    tenant_id      VARCHAR(255) NOT NULL,
    definition_json JSONB       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_connector_definition UNIQUE (key, version, tenant_id)
);

CREATE INDEX idx_cdv2_tenant ON admin_service.connector_definition_v2 (tenant_id);
CREATE INDEX idx_cdv2_key_tenant ON admin_service.connector_definition_v2 (key, tenant_id);

-- -------------------------------------------------------------------------
-- 2. design_audit_log
-- -------------------------------------------------------------------------
CREATE TABLE admin_service.design_audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    principal       VARCHAR(255),
    endpoint        VARCHAR(500) NOT NULL,
    connector_key   VARCHAR(255),
    operation_id    VARCHAR(255),
    direction       VARCHAR(10),
    called_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dal_tenant_at ON admin_service.design_audit_log (tenant_id, called_at DESC);
CREATE INDEX idx_dal_connector  ON admin_service.design_audit_log (connector_key, tenant_id);

-- -------------------------------------------------------------------------
-- 3. Migrate existing rows → connector_definition_v2 (REST transport, one
--    "default" operation per legacy endpoint, production environment only)
-- -------------------------------------------------------------------------
INSERT INTO admin_service.connector_definition_v2 (key, version, tenant_id, definition_json)
SELECT
    ep.connector_key,
    '1.0.0',
    ep.tenant_code,
    jsonb_build_object(
        'apiVersion', 'werkflow.io/connector/v1',
        'kind',       'ConnectorDefinition',
        'metadata', jsonb_build_object(
            'key',         ep.connector_key,
            'displayName', COALESCE(ep.display_name, ep.connector_key),
            'version',     '1.0.0',
            'category',    'data-source'
        ),
        'spec', jsonb_build_object(
            'transport', jsonb_build_object(
                'type',   'rest',
                'config', jsonb_build_object(
                    'baseUrl', ep.base_url
                )
            ),
            'auth', jsonb_build_object(
                'profiles', jsonb_build_array(
                    jsonb_build_object(
                        'id',        'default',
                        'type',      CASE
                                         WHEN cred.auth_scheme = 'BEARER'  THEN 'bearer'
                                         WHEN cred.auth_scheme = 'API_KEY' THEN 'api-key'
                                         WHEN cred.auth_scheme = 'BASIC'   THEN 'basic'
                                         ELSE 'none'
                                     END,
                        'secretKey', ep.connector_key || '-secret'
                    )
                )
            ),
            'operations', jsonb_build_array(
                jsonb_build_object(
                    'id',              'default',
                    'displayName',     'Default Operation',
                    'category',        'action',
                    'input',           COALESCE(ep.sample_schema::jsonb, '{}'::jsonb),
                    'output',          '{}'::jsonb,
                    'transportSpecific', jsonb_build_object(
                        'method', 'POST',
                        'path',   '/'
                    )
                )
            )
        )
    )
FROM admin_service.tenant_service_endpoints ep
LEFT JOIN admin_service.tenant_api_credentials cred
       ON cred.tenant_code  = ep.tenant_code
      AND cred.connector_key = ep.connector_key
WHERE ep.environment = 'production'
  AND ep.active = true
ON CONFLICT (key, version, tenant_id) DO NOTHING;
