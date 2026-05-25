-- V27: Strip the inert `secretKey` field from stored connector definitions
-- (Tier-1 item 3 — connector-def schema hygiene; Roadmap Pre-MVP sequence).
--
-- V18 migrated legacy rows into connector_definition_v2 with
-- spec.auth.profiles[].secretKey = '<connector_key>-secret'. That field was
-- removed from the connector-definition schema on 2026-05-25 (pre-OpenBao
-- SecretsResolver model; credentials now bind per-tenant via credentialRef and
-- resolve from OpenBao server-side — ADR-020 B.6 / ADR-024). The AuthProfile
-- schema is `additionalProperties: false`, so any row still carrying secretKey
-- is now schema-invalid and would be rejected on re-save. This migration brings
-- the stored data into conformance.
--
-- Idempotent: only rewrites rows whose profiles still contain a secretKey, and
-- removing an absent key is a no-op, so re-running changes nothing. V18 is left
-- untouched (never edit an applied migration).

BEGIN;

UPDATE admin_service.connector_definition_v2
SET definition_json = jsonb_set(
        definition_json,
        '{spec,auth,profiles}',
        -- COALESCE guards the degenerate case of an empty profiles array, where
        -- jsonb_agg would return NULL and corrupt the key to JSON null. The WHERE
        -- clause already makes this unreachable for real rows; this keeps it an
        -- explicit invariant rather than an implicit one.
        COALESCE(
            (
                SELECT jsonb_agg(profile - 'secretKey')
                FROM jsonb_array_elements(definition_json #> '{spec,auth,profiles}') AS profile
            ),
            '[]'::jsonb
        )
    )
WHERE definition_json #> '{spec,auth,profiles}' IS NOT NULL
  AND EXISTS (
        SELECT 1
        FROM jsonb_array_elements(definition_json #> '{spec,auth,profiles}') AS profile
        WHERE profile ? 'secretKey'
  );

COMMIT;
