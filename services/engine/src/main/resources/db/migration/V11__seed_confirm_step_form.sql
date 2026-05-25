-- ADR-017 Part 2: stock "confirm-step" form seed.
-- A minimal acknowledgment form for a HUMAN_APPROVAL step used as a "wait for a human
-- acknowledgment" gate (the substitute for the deprecated MANUAL_STEP+confirmationRequired
-- variant). Non-branching: the user ticks the acknowledgment and submits, the process
-- continues; an audit trail (acknowledged + optional comments) is written as process variables.
-- Idempotent: re-running upserts the schema. Flyway wraps this migration in a transaction.

INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type, is_active, created_by, updated_by)
VALUES (
    'confirm-step-form', 1, 'Confirm Step',
    '{
      "type": "default",
      "components": [
        {
          "type": "text",
          "text": "<h3>Confirmation Required</h3><p>Review the details above and confirm to continue.</p>"
        },
        {
          "type": "checkbox",
          "key": "acknowledged",
          "label": "I confirm and acknowledge",
          "validate": { "required": true }
        },
        {
          "type": "textarea",
          "key": "comments",
          "label": "Comments (optional)",
          "validate": { "maxLength": 2000 }
        }
      ]
    }'::jsonb,
    'Stock minimal acknowledgment form for a HUMAN_APPROVAL confirm step (ADR-017 Part 2)',
    'TASK_FORM', true, 'system', 'system'
)
ON CONFLICT (form_key, version) DO UPDATE
    SET schema_json = EXCLUDED.schema_json,
        name        = EXCLUDED.name,
        description = EXCLUDED.description,
        form_type   = EXCLUDED.form_type,
        is_active   = true,
        updated_by  = 'system';
