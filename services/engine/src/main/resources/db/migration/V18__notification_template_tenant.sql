ALTER TABLE notification_templates ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_template_key_key;
ALTER TABLE notification_templates ADD CONSTRAINT uq_notification_template_key_tenant UNIQUE (template_key, tenant_id);
