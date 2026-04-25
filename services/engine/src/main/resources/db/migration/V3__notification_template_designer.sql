-- S28.9: Email Template Designer
-- Adds name, design_json, linked_form_key to notification_templates for Unlayer editor support

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS name          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS design_json   TEXT,
    ADD COLUMN IF NOT EXISTS linked_form_key VARCHAR(100);

-- Back-fill name from template_key for existing rows
UPDATE notification_templates SET name = template_key WHERE name IS NULL;
