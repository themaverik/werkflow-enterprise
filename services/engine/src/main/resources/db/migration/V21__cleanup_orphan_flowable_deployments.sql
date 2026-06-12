-- ============================================================
-- WERKFLOW ENGINE — CLEANUP ORPHAN FLOWABLE DEPLOYMENTS
-- ============================================================
-- Purpose: Remove non-curated DMN deployments from Flowable's
--   ACT_DMN_* tables, and remove any E2E test process deployments
--   left over from test runs that targeted the production DB.
--
-- ADR-031 curated DMNs (keep):
--   capex-approver-resolution.dmn, leave-approval.dmn
--
-- Orphan DMNs to remove (seeded by ProcessExampleDeployer prior to
--   ADR-031 consolidation; no longer referenced by any curated BPMN):
--   budget-routing.dmn, capex-cfo-group.dmn (also capex_cfo_group.dmn),
--   capex-manager-group.dmn (also capex_manager_group.dmn),
--   capex-vp-group.dmn (also capex_vp_group.dmn),
--   leave-routing.dmn, ticket-routing.dmn
--
-- E2E test deployments to remove (AllProcessesDeployAndStartTest uses
--   an in-memory engine, but guard against any test run that may have
--   targeted the production DB):
--   quality-gate-dmn-all, quality-gate-bpmn-all
--
-- Delete order respects FK constraints:
--   ACT_DMN_HI_DECISION_EXECUTION → ACT_DMN_RE_DECISION → ACT_DMN_RE_DEPLOYMENT
--   ACT_RE_PROCDEF → ACT_RE_DEPLOYMENT
--
-- Wrapped in DO/EXCEPTION blocks — Flowable tables may not exist on a
--   fresh database (Flyway runs before Flowable initialises ACT_* tables).
--   On a fresh DB the blocks silently no-op; nothing to clean up anyway.
--
-- Safe to re-run: DELETE WHERE … is idempotent (no rows → no-op).
-- Does NOT touch ACT_RU_* (running instances) or ACT_HI_* history tables.
-- ============================================================

-- ============================================================
-- 1. Remove orphan DMN history executions
--    Must precede act_dmn_re_decision (FK child first).
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_dmn_hi_decision_execution
    WHERE deploy_id_ IN (
        SELECT id_ FROM act_dmn_re_deployment
        WHERE name_ IN (
            'budget-routing.dmn',
            'capex-cfo-group.dmn',
            'capex_cfo_group.dmn',
            'capex-manager-group.dmn',
            'capex_manager_group.dmn',
            'capex-vp-group.dmn',
            'capex_vp_group.dmn',
            'leave-routing.dmn',
            'ticket-routing.dmn',
            'quality-gate-dmn-all'
        )
    );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- ============================================================
-- 2. Remove orphan DMN decision definitions
--    Must precede act_dmn_re_deployment (FK child first).
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_dmn_re_decision
    WHERE deployment_id_ IN (
        SELECT id_ FROM act_dmn_re_deployment
        WHERE name_ IN (
            'budget-routing.dmn',
            'capex-cfo-group.dmn',
            'capex_cfo_group.dmn',
            'capex-manager-group.dmn',
            'capex_manager_group.dmn',
            'capex-vp-group.dmn',
            'capex_vp_group.dmn',
            'leave-routing.dmn',
            'ticket-routing.dmn',
            'quality-gate-dmn-all'
        )
    );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- ============================================================
-- 3. Remove orphan DMN deployments
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_dmn_re_deployment
    WHERE name_ IN (
        'budget-routing.dmn',
        'capex-cfo-group.dmn',
        'capex_cfo_group.dmn',
        'capex-manager-group.dmn',
        'capex_manager_group.dmn',
        'capex-vp-group.dmn',
        'capex_vp_group.dmn',
        'leave-routing.dmn',
        'ticket-routing.dmn',
        'quality-gate-dmn-all'
    );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- ============================================================
-- 4. Remove E2E test process definitions
--    Targets 'E2E Test Process' (deployed by 02-processes.spec.ts via
--    portal UI) and quality-gate-bpmn-all (AllProcessesDeployAndStartTest
--    defensive guard). Must precede act_re_deployment (FK child first).
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_re_procdef
    WHERE name_ = 'E2E Test Process'
       OR deployment_id_ IN (
            SELECT id_ FROM act_re_deployment
            WHERE name_ = 'quality-gate-bpmn-all'
        );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- ============================================================
-- 4.5. Remove act_ge_bytearray rows for deployments to be deleted in step 5.
--      FK act_fk_bytearr_depl: act_ge_bytearray.deployment_id_ → act_re_deployment.id_
--      Must precede act_re_deployment deletion (FK child first).
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_ge_bytearray
    WHERE deployment_id_ IN (
        SELECT id_ FROM act_re_deployment
        WHERE name_ = 'quality-gate-bpmn-all'
           OR id_ IN (
                SELECT DISTINCT d.id_
                FROM act_re_deployment d
                WHERE NOT EXISTS (
                    SELECT 1 FROM act_re_procdef p WHERE p.deployment_id_ = d.id_
                )
                AND d.name_ NOT IN (
                    'capex-approval-process.bpmn20.xml',
                    'leave-request.bpmn20.xml'
                )
            )
    );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- ============================================================
-- 5. Remove orphaned E2E test deployments (no procdef references them)
--    Scoped to quality-gate-bpmn-all only — no broad orphan sweep.
-- ============================================================

DO $$ BEGIN
    DELETE FROM act_re_deployment
    WHERE name_ = 'quality-gate-bpmn-all'
       OR id_ IN (
            -- Deployments whose process definitions were deleted in step 4
            SELECT DISTINCT d.id_
            FROM act_re_deployment d
            WHERE NOT EXISTS (
                SELECT 1 FROM act_re_procdef p WHERE p.deployment_id_ = d.id_
            )
            -- Guard: only clean up deployments that look like test artifacts
            AND d.name_ NOT IN (
                'capex-approval-process.bpmn20.xml',
                'leave-request.bpmn20.xml'
            )
        );
EXCEPTION WHEN undefined_table THEN NULL;
END $$;
