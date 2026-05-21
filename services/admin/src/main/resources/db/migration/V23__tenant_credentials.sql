-- V23: Tenant Credential Registry (metadata-only)
-- Backs M4.12 Phase B.2. The table is a metadata index from
-- (tenant_id, credential_type, label) to an OpenBao path under
-- secret/data/tenants/{tenantId}/{credentialType}/{label}.
--
-- The table NEVER holds plaintext or ciphertext credential values.
-- OpenBao is the source of truth for the data.
-- Per ADR-020 (amendment) and docs/brainstorm/Brainstorm-Credential-Registry-DB.md.

CREATE TABLE IF NOT EXISTS tenant_credentials (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL,
    credential_type VARCHAR(128) NOT NULL,
    label           VARCHAR(100) NOT NULL,
    vault_path      VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rotated_at      TIMESTAMPTZ,

    CONSTRAINT uq_tenant_credentials UNIQUE (tenant_id, credential_type, label),
    CONSTRAINT chk_tenant_credentials_label
        CHECK (label ~ '^[a-z][a-z0-9-]*$')
);

CREATE INDEX IF NOT EXISTS idx_tenant_credentials_tenant_id
    ON tenant_credentials (tenant_id);
