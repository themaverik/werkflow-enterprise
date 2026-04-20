-- V15: Remove unused seed forms and accumulated E2E test form pollution
-- Safe to run on any environment — DELETE on missing rows is a no-op

-- Remove seed forms with no BPMN reference
DELETE FROM form_schemas
WHERE form_key IN (
    'employee-onboarding',
    'contact-request',
    'purchase-request',
    'contactor-registration'
);

-- Remove all accumulated E2E test forms from prior CI runs
DELETE FROM form_schemas WHERE form_key LIKE 'e2e-test-form-%';
