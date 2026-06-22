-- V24__cleanup_legacy_orphan_artifacts.sql
--
-- One-time cleanup of orphan artifacts left by manual test deploys, removed processes,
-- and Playwright test residue. All targets verified to have:
--   act_ru_execution  = 0 rows  (no running instances)
--   act_hi_procinst   = 0 rows  (no historical instances)
--   act_dmn_hi_decision_execution = 0 rows  (no DMN execution history)
--
-- Artifacts removed:
--   FORMS (flowable.form_schemas):
--     - equipment-request-form   : orphan from removed asset-request-process
--     - e2e-delete-test-form     : Playwright test 27 residue (13 versions)
--
--   DMNs (act_dmn_decision + act_dmn_deployment_resource + act_dmn_deployment):
--     - leave-routing   (7 versions, tenant=default) : superseded by leave_approval DMN
--     - budget-routing  (1 version,  tenant=default) : no BPMN references it
--     - ticket-routing  (5 versions, tenant=default) : manual-deploy residue
--
--   BPMNs (act_procdef_info + act_re_procdef + act_ge_bytearray + act_re_deployment):
--     - asset-request-process        (tenant-less, v1)     : process removed from examples
--     - general-approval             (tenant-less, v1)     : process removed from examples
--     - onboarding-checklist         (tenant-less, v1)     : process removed from examples
--     - event-ticket-request         (tenant-less, v1-7)   : superseded by default-tenant copy
--     - capex-approval-process       (tenant-less, v1)     : duplicate of default-tenant copy
--     - leave-request                (tenant-less, v1-9)   : duplicate of default-tenant copy
--     - finance-approval-process     (tenant-less, v1)     : duplicate of default-tenant copy
--     - procurement-approval-process (tenant-less, v1)     : duplicate of default-tenant copy
--     - capex-approval-process       (tenant=erp,  v1)     : ERP test-deploy residue
--     - leave-request                (tenant=erp,  v1)     : ERP test-deploy residue
--     - process / "New Process"      (tenant=default, v4-5): user-authored drafts already deployed
--
-- All DELETEs are no-ops when the artifact is already absent (idempotent).
-- Wrapped in a single transaction so a partial failure rolls back cleanly.

BEGIN;

-- ============================================================
-- SECTION 1: Forms
-- ============================================================

DELETE FROM flowable.form_schemas
WHERE form_key IN ('equipment-request-form', 'e2e-delete-test-form');

-- ============================================================
-- SECTION 2: DMNs
-- Target keys: leave-routing, budget-routing, ticket-routing (tenant=default)
-- Order: decisions → resources → deployments (FK: resource → deployment)
-- ============================================================

DELETE FROM public.act_dmn_hi_decision_execution
WHERE decision_definition_id_ IN (
    SELECT id_ FROM public.act_dmn_decision
    WHERE key_ IN ('leave-routing', 'budget-routing', 'ticket-routing')
      AND (tenant_id_ = 'default' OR tenant_id_ IS NULL OR tenant_id_ = '')
);

DELETE FROM public.act_dmn_decision
WHERE key_ IN ('leave-routing', 'budget-routing', 'ticket-routing')
  AND (tenant_id_ = 'default' OR tenant_id_ IS NULL OR tenant_id_ = '');

DELETE FROM public.act_dmn_deployment_resource
WHERE deployment_id_ IN (
    SELECT id_ FROM public.act_dmn_deployment
    WHERE name_ IN ('leave-routing.dmn', 'budget-routing.dmn', 'ticket-routing.dmn')
      AND (tenant_id_ = 'default' OR tenant_id_ IS NULL OR tenant_id_ = '')
);

DELETE FROM public.act_dmn_deployment
WHERE name_ IN ('leave-routing.dmn', 'budget-routing.dmn', 'ticket-routing.dmn')
  AND (tenant_id_ = 'default' OR tenant_id_ IS NULL OR tenant_id_ = '');

-- ============================================================
-- SECTION 3: BPMNs
-- Order: act_procdef_info → act_re_procdef → act_ge_bytearray → act_re_deployment
-- act_re_procdef.deployment_id_ is NOT a FK; act_ge_bytearray.deployment_id_ IS a FK.
-- ============================================================

-- Collect target deployment IDs into a reusable subquery.
-- Tenant-less orphans (NULL or empty tenant_id_):
--   asset-request-process, general-approval, onboarding-checklist, event-ticket-request,
--   capex-approval-process, leave-request, finance-approval-process, procurement-approval-process
-- ERP-tenant duplicates:
--   capex-approval-process (erp), leave-request (erp)
-- Default-tenant user drafts:
--   process (default, versions >= 4)

-- 3a. Remove procdef_info rows (FK → act_re_procdef)
DELETE FROM public.act_procdef_info
WHERE proc_def_id_ IN (
    SELECT id_ FROM public.act_re_procdef
    WHERE (
        (key_ IN (
            'asset-request-process', 'general-approval', 'onboarding-checklist',
            'event-ticket-request', 'capex-approval-process', 'leave-request',
            'finance-approval-process', 'procurement-approval-process'
         ) AND (tenant_id_ IS NULL OR tenant_id_ = ''))
        OR (key_ IN ('capex-approval-process', 'leave-request')
            AND tenant_id_ = 'erp')
        OR (key_ = 'process' AND tenant_id_ = 'default' AND version_ >= 4)
    )
);

-- 3b. Remove process definitions
DELETE FROM public.act_re_procdef
WHERE (
    (key_ IN (
        'asset-request-process', 'general-approval', 'onboarding-checklist',
        'event-ticket-request', 'capex-approval-process', 'leave-request',
        'finance-approval-process', 'procurement-approval-process'
     ) AND (tenant_id_ IS NULL OR tenant_id_ = ''))
    OR (key_ IN ('capex-approval-process', 'leave-request')
        AND tenant_id_ = 'erp')
    OR (key_ = 'process' AND tenant_id_ = 'default' AND version_ >= 4)
);

-- 3c. Remove bytearrays linked to orphan deployments
-- We identify orphan deployments by the deployment_id_ values that were referenced by
-- the now-deleted procdef rows. Since procdef rows are already gone, we identify deployments
-- by name pattern — safe because these deployment names are unique to these orphan processes.
DELETE FROM public.act_ge_bytearray
WHERE deployment_id_ IN (
    SELECT id_ FROM public.act_re_deployment
    WHERE (
        (name_ IN (
            'asset-request-process.bpmn20.xml', 'general-approval.bpmn20.xml',
            'onboarding-checklist.bpmn20.xml', 'event-ticket-request.bpmn20.xml',
            'capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml',
            'finance-approval-process.bpmn20.xml', 'procurement-approval-process.bpmn20.xml'
         ) AND (tenant_id_ IS NULL OR tenant_id_ = ''))
        OR (name_ IN ('capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml')
            AND tenant_id_ = 'erp')
        OR (name_ = 'process.bpmn20.xml' AND tenant_id_ = 'default')
    )
);

-- 3d. Remove the deployment records themselves
DELETE FROM public.act_re_deployment
WHERE (
    (name_ IN (
        'asset-request-process.bpmn20.xml', 'general-approval.bpmn20.xml',
        'onboarding-checklist.bpmn20.xml', 'event-ticket-request.bpmn20.xml',
        'capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml',
        'finance-approval-process.bpmn20.xml', 'procurement-approval-process.bpmn20.xml'
     ) AND (tenant_id_ IS NULL OR tenant_id_ = ''))
    OR (name_ IN ('capex-approval-process.bpmn20.xml', 'leave-request.bpmn20.xml')
        AND tenant_id_ = 'erp')
    OR (name_ = 'process.bpmn20.xml' AND tenant_id_ = 'default')
);

COMMIT;
