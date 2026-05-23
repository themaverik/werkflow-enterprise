-- V25: Migrate connector auth off shadow AES storage onto the OpenBao credential model
-- (M4.12 Phase B.6). Replaces tenant_api_credentials.secret_value (AES-256-GCM) with a
-- credential_ref pointer into the tenant_credentials / OpenBao model, closing the last
-- EncryptionService credential path.
--
-- Existing rows hold AES-encrypted secrets keyed to a local dev master key and are
-- unrecoverable through OpenBao. They are runtime-created throwaway dev data (no SQL seed
-- inserts into these tables). Consistent with the V24 datasource decision (B.5 amendment):
-- TRUNCATE and require re-registration via the new credential-picker flow.
--
-- credential_ref is nullable: authScheme = 'NONE' connectors carry no credential.

BEGIN;

-- A connector is an (endpoint, credential) pair created together by ConnectorService.
-- Truncate both so the demo reseeds cleanly with no orphaned endpoints or stale secrets.
TRUNCATE TABLE admin_service.tenant_api_credentials;
TRUNCATE TABLE admin_service.tenant_service_endpoints;

ALTER TABLE admin_service.tenant_api_credentials DROP COLUMN secret_value;

ALTER TABLE admin_service.tenant_api_credentials ADD COLUMN credential_ref VARCHAR(100);

ALTER TABLE admin_service.tenant_api_credentials
    ADD CONSTRAINT chk_credential_ref_pattern
    CHECK (credential_ref IS NULL OR credential_ref ~ '^[a-z][a-z0-9-]*$');

COMMIT;
