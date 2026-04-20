-- V20: Change deliveryDate component from datetime to datetime+subtype:date
-- The plain "datetime" type sends a UTC-normalised ISO timestamp, which when the
-- user is in a positive UTC offset (e.g. IST +05:30) produces a date one day
-- earlier than selected (e.g. "2026-03-27T18:30:00Z" for March 28 midnight IST).
-- subtype:"date" sends only "YYYY-MM-DD" with no timezone conversion.
--
-- Optional business workflow context: Fixes the deliveryDate field in the bundled
-- asset-request example form. Safe to run without the optional business module — updates
-- form_schemas only and is a no-op if the asset-request form row does not exist.

UPDATE form_schemas
SET schema_json = jsonb_set(
    schema_json,
    '{components}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN component->>'key' = 'deliveryDate'
                THEN component || '{"subtype": "date"}'::jsonb
                ELSE component
            END
        )
        FROM jsonb_array_elements(schema_json->'components') AS component
    )
)
WHERE form_key = 'asset-request-form';
