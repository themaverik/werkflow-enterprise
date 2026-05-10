-- V20: Tenant Datasource Registry
-- Stores JDBC datasource configurations per tenant for the database connector transport.
-- Passwords are NOT stored here — passwordSecretRef is a key into the platform secrets manager.

CREATE TABLE IF NOT EXISTS tenant_datasource (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  VARCHAR(100) NOT NULL,
    ref                        VARCHAR(100) NOT NULL,
    jdbc_url                   TEXT         NOT NULL,
    driver_class_name          VARCHAR(200) NOT NULL,
    username                   VARCHAR(200) NOT NULL,
    password_secret_ref        VARCHAR(500) NOT NULL,  -- secret manager key reference, NOT plaintext
    dialect                    VARCHAR(50),
    pool_min_size              INTEGER      NOT NULL DEFAULT 1,
    pool_max_size              INTEGER      NOT NULL DEFAULT 5,
    connection_timeout_seconds INTEGER      NOT NULL DEFAULT 5,
    idle_timeout_seconds       INTEGER      NOT NULL DEFAULT 600,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tenant_datasource_ref UNIQUE (tenant_id, ref),
    CONSTRAINT chk_ref_pattern CHECK (ref ~ '^[a-z][a-z0-9-]*$')
);

CREATE INDEX IF NOT EXISTS idx_tenant_datasource_tenant_id ON tenant_datasource (tenant_id);
