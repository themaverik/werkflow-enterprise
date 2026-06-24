-- V29: Tenant-scope form_schemas (D2). Each tenant owns its own form rows.
-- Mirrors V17 (process_draft) / V18 (notification_templates) tenant pattern.
ALTER TABLE form_schemas ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';

-- Replace the global (form_key, version) unique with a tenant-composite key
-- so two tenants can each hold form_key X version N.
ALTER TABLE form_schemas DROP CONSTRAINT IF EXISTS uk_form_key_version;
ALTER TABLE form_schemas ADD CONSTRAINT uk_form_key_version_tenant UNIQUE (form_key, version, tenant_id);
