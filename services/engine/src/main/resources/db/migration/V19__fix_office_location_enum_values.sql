-- V19: Fix officeLocation values to match OfficeLocation enum in business-service
-- Previous migrations used localised shortcodes (KL, PG, JB, REMOTE) that do not
-- correspond to any OfficeLocation enum constant, causing Jackson to deserialise
-- the submitted value as null and the @NotNull constraint to fire on every submission.
--
-- Optional business workflow context: This migration patches the asset-request form schema
-- to align with the optional business-service OfficeLocation enum. It is safe to run without
-- the business module deployed — it updates form_schemas only and has no effect if the
-- business-service is not present.

UPDATE form_schemas
SET schema_json = jsonb_set(
    schema_json,
    '{components}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN component->>'key' = 'officeLocation'
                THEN jsonb_set(
                    component,
                    '{values}',
                    '[
                        {"label": "Seattle, USA",        "value": "SEATTLE_US"},
                        {"label": "Bangalore, India",    "value": "BANGALORE_IN"},
                        {"label": "Shillong, India",     "value": "SHILLONG_IN"},
                        {"label": "Stockholm, Sweden",   "value": "STOCKHOLM_SE"},
                        {"label": "Melbourne, Australia","value": "MELBOURNE_AU"}
                    ]'::jsonb
                )
                ELSE component
            END
        )
        FROM jsonb_array_elements(schema_json->'components') AS component
    )
)
WHERE form_key = 'asset-request-form';
