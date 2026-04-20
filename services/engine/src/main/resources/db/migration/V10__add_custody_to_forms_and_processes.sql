-- ================================================================================================
-- Department Custody for Forms and Processes
-- ================================================================================================
-- Description: Adds department ownership tracking to form_schemas, and creates a
--              process_custody table for tracking process definition ownership.
-- Version: 10
-- ================================================================================================

-- Add custody columns to form_schemas
ALTER TABLE form_schemas
    ADD COLUMN IF NOT EXISTS owning_department VARCHAR(100),
    ADD COLUMN IF NOT EXISTS created_by_department VARCHAR(100);

-- Process custody table — Flowable manages process definitions externally,
-- so we track ownership in a separate table keyed by process definition key.
CREATE TABLE IF NOT EXISTS process_custody (
    process_definition_key  VARCHAR(255) PRIMARY KEY,
    owning_department        VARCHAR(100) NOT NULL,
    created_by               VARCHAR(255),
    created_by_department    VARCHAR(100),
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_process_custody_dept ON process_custody(owning_department);

COMMENT ON TABLE process_custody IS 'Tracks department ownership of Flowable process definitions for edit/delete rights';
COMMENT ON COLUMN process_custody.owning_department IS 'Department that has custody (edit/delete rights) over this process';
COMMENT ON COLUMN form_schemas.owning_department IS 'Department that has custody (edit/delete rights) over this form';
