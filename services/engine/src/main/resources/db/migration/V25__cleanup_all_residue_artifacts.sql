-- V25__cleanup_all_residue_artifacts.sql
--
-- One-time cleanup of all remaining residue artifacts so that the engine DB
-- contains ONLY current, active, seeded artifacts after this migration runs.
-- A user authoring a new BPMN will never see stale duplicates in any dropdown.
--
-- Pre-write verification (all queries returned 0 — safe to proceed):
--   act_ru_execution  referencing test-tenant procdefs          = 0
--   act_hi_procinst   referencing test-tenant procdefs          = 0
--   act_dmn_hi_decision_execution for tenants '',erp,test       = 0
--   act_dmn_hi_decision_execution for default-tenant DMN v1     = 0
--   act_re_procdef version dupes inside default tenant          = 0
--
-- Artifacts removed:
--
--   SECTION 1 — Forms (flowable.form_schemas)
--     leave-request-form v1–v7 : stale inactive versions; v8 is the active seed.
--       v1 incorrectly carried is_active=true alongside v8 — both deleted here.
--
--   SECTION 2 — BPMNs (act_procdef_info → act_re_procdef → act_ge_bytearray → act_re_deployment)
--     capex-approval-process  (tenant=test, v1) : Playwright / test-tenant residue
--     leave-request           (tenant=test, v1) : Playwright / test-tenant residue
--
--   SECTION 3 — DMNs (act_dmn_decision → act_dmn_deployment_resource → act_dmn_deployment)
--     default tenant v1 decisions (capex_cfo_group, capex_manager_group, capex_vp_group,
--                                   leave_approval, procurement_matrix) : superseded by v2
--     erp     tenant v1 decisions (capex_cfo_group, capex_manager_group, capex_vp_group,
--                                   leave_approval)                     : test-deploy residue
--     test    tenant v1 decisions (capex_cfo_group, capex_manager_group, capex_vp_group,
--                                   leave_approval)                     : test-deploy residue
--
-- All DELETEs are no-ops when the target rows are already absent (idempotent).
-- Wrapped in a single transaction so a partial failure rolls back cleanly.
-- process_draft rows are NOT touched — they are auto-pruned by ProcessExampleDeployer.
-- act_hi_* history tables are NOT touched — verified empty for all targets above.

BEGIN;

-- ============================================================
-- SECTION 1: Forms
-- Delete leave-request-form versions 1-7; keep v8 (the active seed).
-- No FK references from form_submissions to any of these versions (verified).
-- ============================================================

DELETE FROM flowable.form_schemas
WHERE form_key = 'leave-request-form'
  AND version <= 7;

-- ============================================================
-- SECTION 2: BPMNs — test-tenant residue
-- Targets:
--   capex-approval-process (tenant=test, v1)
--   leave-request          (tenant=test, v1)
--
-- FK order: act_procdef_info → act_re_procdef
--           act_ge_bytearray.deployment_id_ → act_re_deployment.id_
--           act_re_procdef.deployment_id_ is NOT a FK (safe to delete separately)
-- ============================================================

-- 2a. procdef_info rows (FK → act_re_procdef; verified 0 rows but kept for idempotency)
DELETE FROM public.act_procdef_info
WHERE proc_def_id_ IN (
    SELECT id_ FROM public.act_re_procdef
    WHERE key_       IN ('capex-approval-process', 'leave-request')
      AND tenant_id_ = 'test'
);

-- 2b. Process definitions
DELETE FROM public.act_re_procdef
WHERE key_       IN ('capex-approval-process', 'leave-request')
  AND tenant_id_ = 'test';

-- 2c. Bytearrays keyed to the test-tenant deployments
DELETE FROM public.act_ge_bytearray
WHERE deployment_id_ IN (
    SELECT id_ FROM public.act_re_deployment
    WHERE name_      IN ('capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml')
      AND tenant_id_ = 'test'
);

-- 2d. Deployment records
DELETE FROM public.act_re_deployment
WHERE name_      IN ('capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml')
  AND tenant_id_ = 'test';

-- ============================================================
-- SECTION 3: DMNs — stale v1 decisions across default/erp/test tenants
-- Targets (version_ = 1 only within each tenant group):
--   default : capex_cfo_group, capex_manager_group, capex_vp_group, leave_approval,
--             procurement_matrix
--   erp     : capex_cfo_group, capex_manager_group, capex_vp_group, leave_approval
--   test    : capex_cfo_group, capex_manager_group, capex_vp_group, leave_approval
--
-- FK order: act_dmn_decision → act_dmn_deployment_resource → act_dmn_deployment
-- act_dmn_hi_decision_execution verified = 0 rows for all targets (no history rows to clean).
-- ============================================================

-- 3a. Decision rows
DELETE FROM public.act_dmn_decision
WHERE version_ = 1
  AND (
      (tenant_id_ = 'default' AND key_ IN (
          'capex_cfo_group', 'capex_manager_group', 'capex_vp_group',
          'leave_approval', 'procurement_matrix'
      ))
      OR
      (tenant_id_ IN ('erp', 'test') AND key_ IN (
          'capex_cfo_group', 'capex_manager_group', 'capex_vp_group',
          'leave_approval'
      ))
  );

-- 3b. Deployment resource rows (linked to the now-removed decision deployments)
--     We identify the stale deployments by the known deployment IDs captured at
--     pre-write verification time. Using explicit IDs makes the predicate safe
--     against re-seeded deployments that may share the same name in future runs.
DELETE FROM public.act_dmn_deployment_resource
WHERE deployment_id_ IN (
    -- default-tenant v1 DMN deployments (3 bundles: capex group, leave, procurement)
    '819981e1-5f66-11f1-9d98-0242ac120008',
    '81ac4696-5f66-11f1-9d98-0242ac120008',
    '81aeb799-5f66-11f1-9d98-0242ac120008',
    -- erp-tenant v1 DMN deployments (2 bundles)
    'a195b20d-668d-11f1-8c09-0242ac12000a',
    'a1ead6f5-668d-11f1-8c09-0242ac12000a',
    -- test-tenant v1 DMN deployments (2 bundles)
    '74f99adf-6683-11f1-8c09-0242ac12000a',
    '75609a17-6683-11f1-8c09-0242ac12000a'
);

-- 3c. Deployment records themselves
DELETE FROM public.act_dmn_deployment
WHERE id_ IN (
    '819981e1-5f66-11f1-9d98-0242ac120008',
    '81ac4696-5f66-11f1-9d98-0242ac120008',
    '81aeb799-5f66-11f1-9d98-0242ac120008',
    'a195b20d-668d-11f1-8c09-0242ac12000a',
    'a1ead6f5-668d-11f1-8c09-0242ac12000a',
    '74f99adf-6683-11f1-8c09-0242ac12000a',
    '75609a17-6683-11f1-8c09-0242ac12000a'
);

COMMIT;
