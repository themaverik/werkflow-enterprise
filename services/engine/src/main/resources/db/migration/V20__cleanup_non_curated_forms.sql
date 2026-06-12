-- ============================================================
-- WERKFLOW ENGINE — CLEANUP NON-CURATED FORM SCHEMAS
-- ============================================================
-- Purpose: Remove all form_schemas rows that are NOT part of the
--   4 curated ADR-031 example forms, and purge any E2E test
--   artifact forms left over from interrupted test runs.
--
-- KEEP (ADR-031 curated set, seeded by ExampleSeedService):
--   capex-request-form, capex-approval-form,
--   leave-request-form, leave-approval-form
--
-- DELETE (seeded by V1/V2/V8/V11 — non-curated examples):
--   asset-request-form, event-ticket-form, budget-request-form,
--   general-approval-form, general-approval-decision,
--   document-review-form, document-review-decision,
--   onboarding-checklist-form, procurement-request-form,
--   vendor-selection, quotation-review, procurement-approval,
--   confirm-step-form
--
-- DELETE (E2E test artifacts from interrupted runs):
--   form_key LIKE 'e2e-test-form-%'
--
-- Safe to re-run: DELETE WHERE … is idempotent (no rows → no-op).
-- Safe on a fresh DB: form_schemas is always present (created V1).
-- Does NOT touch Flowable process/DMN tables.
-- ============================================================

DELETE FROM form_schemas
WHERE form_key IN (
    -- V1 / V2 / V8 seeds — non-curated examples
    'asset-request-form',
    'event-ticket-form',
    'budget-request-form',
    'general-approval-form',
    'general-approval-decision',
    'document-review-form',
    'document-review-decision',
    'onboarding-checklist-form',
    'procurement-request-form',
    'vendor-selection',
    'quotation-review',
    'procurement-approval',
    -- V11 seed — ADR-017 confirm-step helper (not part of ADR-031 curated set)
    'confirm-step-form'
);

-- Remove E2E test artifact forms left over from interrupted test runs.
DELETE FROM form_schemas
WHERE form_key LIKE 'e2e-test-form-%';
