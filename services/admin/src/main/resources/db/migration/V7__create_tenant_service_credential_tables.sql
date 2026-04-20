CREATE TABLE tenant_service_endpoints (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_code  VARCHAR(50)  NOT NULL,
    service_key  VARCHAR(100) NOT NULL,
    base_url     VARCHAR(500) NOT NULL,
    environment  VARCHAR(50)  NOT NULL DEFAULT 'development',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_code, service_key, environment)
);

CREATE INDEX idx_tenant_svc_ep_tenant ON tenant_service_endpoints (tenant_code);

CREATE TABLE tenant_api_credentials (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_code     VARCHAR(50)  NOT NULL,
    credential_key  VARCHAR(100) NOT NULL,
    label           VARCHAR(200),
    auth_scheme     VARCHAR(30)  NOT NULL,
    secret_ref      VARCHAR(200) NOT NULL,
    header_name     VARCHAR(100),
    allowed_origins JSONB,
    key_prefix      VARCHAR(20),
    last_used_at    TIMESTAMP,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_code, credential_key),
    CONSTRAINT chk_auth_scheme CHECK (
        auth_scheme IN ('API_KEY', 'BEARER', 'BASIC', 'OAUTH2_CLIENT_CREDENTIALS')
    )
);

CREATE INDEX idx_tenant_api_cred_tenant ON tenant_api_credentials (tenant_code);
