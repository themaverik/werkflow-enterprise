-- ================================================================================================
-- Form Schemas Management
-- ================================================================================================
-- Description: Creates tables for managing form-js schemas and form submissions in Werkflow
-- Author: Werkflow Team
-- Date: 2025-11-30
-- Version: 6
-- ================================================================================================

-- Create form_schemas table to store form-js schema definitions
CREATE TABLE IF NOT EXISTS form_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_key VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    schema_json JSONB NOT NULL,
    description TEXT,
    form_type VARCHAR(50) DEFAULT 'TASK_FORM',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uk_form_key_version UNIQUE(form_key, version)
);

-- Create index on form_key for faster lookups
CREATE INDEX IF NOT EXISTS idx_form_schemas_form_key ON form_schemas(form_key);

-- Create index on form_type for filtering
CREATE INDEX IF NOT EXISTS idx_form_schemas_form_type ON form_schemas(form_type);

-- Create index on is_active for filtering active forms
CREATE INDEX IF NOT EXISTS idx_form_schemas_is_active ON form_schemas(is_active);

-- Create partial index for latest active version
CREATE INDEX IF NOT EXISTS idx_form_schemas_latest_active
ON form_schemas(form_key, version DESC)
WHERE is_active = true;

-- Create GIN index on schema_json for JSONB queries
CREATE INDEX IF NOT EXISTS idx_form_schemas_schema_json ON form_schemas USING GIN (schema_json);

-- ================================================================================================
-- Form Submissions History
-- ================================================================================================

-- Create form_submissions table to track all form submissions
CREATE TABLE IF NOT EXISTS form_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_schema_id UUID NOT NULL REFERENCES form_schemas(id),
    task_id VARCHAR(255),
    process_instance_id VARCHAR(255),
    form_data JSONB NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    submitted_by VARCHAR(255) NOT NULL,
    validation_errors JSONB,
    submission_status VARCHAR(50) DEFAULT 'SUBMITTED',
    CONSTRAINT fk_form_schema FOREIGN KEY (form_schema_id) REFERENCES form_schemas(id) ON DELETE CASCADE
);

-- Create index on task_id for task-based queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_task_id ON form_submissions(task_id);

-- Create index on process_instance_id for process-based queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_process_instance_id ON form_submissions(process_instance_id);

-- Create index on submitted_by for user-based queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_submitted_by ON form_submissions(submitted_by);

-- Create index on submitted_at for chronological queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_submitted_at ON form_submissions(submitted_at DESC);

-- Create GIN index on form_data for JSONB queries
CREATE INDEX IF NOT EXISTS idx_form_submissions_form_data ON form_submissions USING GIN (form_data);

-- ================================================================================================
-- Comments
-- ================================================================================================

COMMENT ON TABLE form_schemas IS 'Stores form-js schema definitions for Werkflow forms';
COMMENT ON COLUMN form_schemas.form_key IS 'Unique identifier for the form (e.g., capex-request-form)';
COMMENT ON COLUMN form_schemas.version IS 'Version number for schema evolution';
COMMENT ON COLUMN form_schemas.schema_json IS 'Complete form-js schema definition in JSON format';
COMMENT ON COLUMN form_schemas.form_type IS 'Type of form: PROCESS_START, TASK_FORM, APPROVAL, CUSTOM';
COMMENT ON COLUMN form_schemas.is_active IS 'Whether this version is currently active';

COMMENT ON TABLE form_submissions IS 'Audit log of all form submissions';
COMMENT ON COLUMN form_submissions.form_data IS 'Submitted form data in JSON format';
COMMENT ON COLUMN form_submissions.submission_status IS 'Status: SUBMITTED, VALIDATED, COMPLETED, FAILED';
