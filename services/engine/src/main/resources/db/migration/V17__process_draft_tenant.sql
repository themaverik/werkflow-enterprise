ALTER TABLE process_draft ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE process_draft DROP CONSTRAINT IF EXISTS process_draft_process_key_key;
ALTER TABLE process_draft ADD CONSTRAINT uq_process_draft_key_tenant UNIQUE (process_key, tenant_id);
