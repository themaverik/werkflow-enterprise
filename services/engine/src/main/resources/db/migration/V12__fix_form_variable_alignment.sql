-- V12: Align seed form field keys with DMN/BPMN process variable names.
-- V8 seeded these forms before the variable names were finalised in ADR-025/Item-14.
-- Editing V8 would cause Flyway checksum failures on existing databases;
-- these UPDATE statements are idempotent and safe to run on both fresh and live schemas.

-- capex-request-form: field key 'department' → 'capexOwner'
-- The DMN approverGroup routing reads ${capexOwner}; the old key caused a silent null lookup.
UPDATE form_schemas
SET schema_json = REPLACE(schema_json::text, '"key": "department"', '"key": "capexOwner"')::jsonb
WHERE form_key = 'capex-request-form';

-- leave-request-form: field key 'totalDays' → 'leaveDays', label 'Total Days' → 'Number of Days'
-- Leave routing DMN reads leaveDays; label updated to match spec selector /number of days/i.
UPDATE form_schemas
SET schema_json = REPLACE(
    REPLACE(schema_json::text, '"key": "totalDays"', '"key": "leaveDays"'),
    '"label": "Total Days"', '"label": "Number of Days"'
)::jsonb
WHERE form_key = 'leave-request-form';
