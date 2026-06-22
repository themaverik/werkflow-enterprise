-- V26__cleanup_residue_v2.sql
--
-- Follow-up cleanup for residue NOT covered by V25.
-- V25 explicitly listed 'erp' and 'test' tenants for DMN cleanup but missed
-- the tenantless ('' / NULL tenant_id_) DMN duplicates. V25 also assumed v8
-- was the canonical leave-request-form, but the canonical seed is v1
-- (recreated by ProcessExampleDeployer.seedFormSchemas on every boot via
-- ON CONFLICT (form_key, version) DO UPDATE). v8 is dev leftover.
--
-- After this migration runs, the engine DB will contain ONLY:
--   - 4 BPMNs in default tenant (capex / finance / leave / procurement)
--   - 5 DMNs in default tenant at v2 (capex × 3, leave_approval, procurement_matrix)
--   - 9 forms at v1 (8 of 9 already at v1; leave-request-form's v8 deleted here
--     so the deployer's v1 is sole authoritative)
--
-- Pre-write verification (all queries returned 0 — safe to proceed):
--   act_dmn_hi_decision_execution for tenantless decisions = 0
--   form_submissions / variables referencing leave-request-form@8 = 0
--   (form pinning by version is unused for example forms — verified)
--
-- Idempotency: every DELETE is a no-op if rows are already absent.
--
-- Atomicity: wrapped in single BEGIN/COMMIT.

BEGIN;

-- ============================================================================
-- SECTION 1 — Tenantless DMN cleanup (missed by V25)
--
-- Five tenantless v1 decisions remained after V25:
--   capex_cfo_group, capex_manager_group, capex_vp_group,
--   leave_approval, procurement_matrix
--
-- These predate the default-tenant seed (DmnExampleDeployer @DependsOn).
-- Ordering: decision -> deployment_resource -> deployment.
-- ============================================================================

-- 1a. Delete tenantless DMN decisions
DELETE FROM public.act_dmn_decision
WHERE tenant_id_ IS NULL OR tenant_id_ = '';

-- 1b. Delete tenantless DMN deployment resources
DELETE FROM public.act_dmn_deployment_resource
WHERE deployment_id_ IN (
    SELECT id_ FROM public.act_dmn_deployment
    WHERE tenant_id_ IS NULL OR tenant_id_ = ''
);

-- 1c. Delete tenantless DMN deployments
DELETE FROM public.act_dmn_deployment
WHERE tenant_id_ IS NULL OR tenant_id_ = '';

-- ============================================================================
-- SECTION 2 — Wipe leave-request-form leftover v8
--
-- ProcessExampleDeployer.seedFormSchemas inserts leave-request-form at
-- version 1 on every engine boot (ON CONFLICT DO UPDATE on (form_key, version)).
-- v8 is dev leftover from prior form-editor work and was incorrectly assumed
-- by V25 to be canonical. v1 is the actual seed.
--
-- No BPMN references leave-request-form@8 (all reference by bare key, which
-- resolves to active v1). Verified no act_ru_variable rows reference
-- a versioned form key.
-- ============================================================================

DELETE FROM flowable.form_schemas
WHERE form_key = 'leave-request-form' AND version > 1;

COMMIT;
