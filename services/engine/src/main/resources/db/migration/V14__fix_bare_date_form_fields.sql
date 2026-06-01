-- V14: Convert bare "type": "date" fields to form-js datetime+subtype:date.
--
-- form-js has no top-level "date" or "time" component type. Fields declared as
-- {"type":"date", ...} silently fail to render — the form area appears blank.
-- The supported form is {"type":"datetime", "subtype":"date", ...}. Five seed
-- forms (asset-request, event-ticket, leave-request×3) were fixed earlier in
-- enterprise 0960ab0; three forms were missed: capex-request, onboarding-checklist,
-- procurement-request. Each has exactly one bare date field.
--
-- The text-level REPLACE is scoped via WHERE clause to the three affected forms
-- and is idempotent (running again finds no "type": "date" to replace).

UPDATE flowable.form_schemas
SET schema_json = REPLACE(
    schema_json::text,
    '"type": "date"',
    '"type": "datetime", "subtype": "date"'
)::jsonb
WHERE form_key IN (
    'capex-request-form',
    'onboarding-checklist-form',
    'procurement-request-form'
);
