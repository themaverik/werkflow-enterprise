-- ============================================================
-- WERKFLOW ENGINE — WIPE UNTENANT-SCOPED HISTORIC ACTIVITY
-- ============================================================
-- Purpose: Remove all finished-process-instance history that was
--   created before tenant isolation was enforced in the dashboard
--   controller (WorkflowDashboardController). All rows in these
--   tables were written without a tenant_id_ value, so they
--   cannot be attributed to a specific tenant and must be wiped.
--
-- Scope: finished process instances only (end_time_ IS NOT NULL).
--   Running instances (end_time_ IS NULL) are NOT touched.
--
-- Delete order respects FK constraints:
--   act_hi_varinst  → act_hi_procinst (proc_inst_id_)
--   act_hi_taskinst → act_hi_procinst (proc_inst_id_)
--   act_hi_actinst  → act_hi_procinst (proc_inst_id_)
--   act_hi_detail   → act_hi_procinst (proc_inst_id_)
--   act_hi_comment  → act_hi_procinst (proc_inst_id_)
--   act_hi_attachment → act_hi_procinst (proc_inst_id_)
--   act_hi_procinst (root, deleted last)
--
-- Wrapped in DO/EXCEPTION blocks — Flowable initialises ACT_*
--   tables at first startup; a fresh DB has no rows to clean up
--   and these blocks will no-op silently.
--
-- Safe to re-run: all DELETEs are idempotent (no rows → no-op).
-- ============================================================

-- 1. Variable instances for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_varinst
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 2. Task instances for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_taskinst
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 3. Activity instances for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_actinst
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 4. Detail log for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_detail
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 5. Comments for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_comment
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 6. Attachments for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_attachment
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 6a. Identity links for finished processes
DO $$ BEGIN
    DELETE FROM act_hi_identitylink
    WHERE proc_inst_id_ IN (
        SELECT id_ FROM act_hi_procinst WHERE end_time_ IS NOT NULL
    );
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;

-- 7. Finished process instances (root table, deleted last)
DO $$ BEGIN
    DELETE FROM act_hi_procinst WHERE end_time_ IS NOT NULL;
EXCEPTION WHEN undefined_table THEN NULL;
         WHEN OTHERS THEN RAISE;
END $$;
