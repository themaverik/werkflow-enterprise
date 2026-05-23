-- V24: Migrate tenant_datasource off shadow AES storage onto the OpenBao credential model.
-- Per ADR-020 (B.5 amendment) and
-- docs/superpowers/specs/2026-05-23-m4.12-b5-database-credential-migration-design.md.
--
-- Existing rows hold AES-encrypted passwords keyed to a local dev master key and are
-- unrecoverable through OpenBao. They are throwaway test data (no SQL seed inserts into
-- this table). Decision D2: TRUNCATE and require re-registration via the new credential flow.
-- TRUNCATE must run before ADD COLUMN ... NOT NULL so the constraint applies to an empty table.

BEGIN;

TRUNCATE TABLE tenant_datasource;

ALTER TABLE tenant_datasource DROP COLUMN encrypted_password;
ALTER TABLE tenant_datasource DROP COLUMN username;

ALTER TABLE tenant_datasource ADD COLUMN credential_ref VARCHAR(100) NOT NULL;

ALTER TABLE tenant_datasource ADD CONSTRAINT chk_credential_ref_pattern
    CHECK (credential_ref ~ '^[a-z][a-z0-9-]*$');

COMMIT;
