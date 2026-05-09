-- ADR-010: Three-axis categorization model for process/form/DMN artifacts.
-- department_code: visibility scoping (null = all departments)
-- category_code: catalog grouping (references tenant-registered categories in admin-service)
-- tags: free-form search and discovery tags

ALTER TABLE process_draft
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS category_code   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tags            TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_process_draft_dept ON process_draft(department_code);
CREATE INDEX IF NOT EXISTS idx_process_draft_cat  ON process_draft(category_code);
CREATE INDEX IF NOT EXISTS idx_process_draft_tags ON process_draft USING GIN(tags);

ALTER TABLE form_schemas
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS category_code   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tags            TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_form_schemas_dept ON form_schemas(department_code);
CREATE INDEX IF NOT EXISTS idx_form_schemas_cat  ON form_schemas(category_code);
CREATE INDEX IF NOT EXISTS idx_form_schemas_tags ON form_schemas USING GIN(tags);
