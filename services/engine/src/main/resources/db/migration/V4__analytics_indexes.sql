-- M6 Group A: analytics performance indexes.
-- All queries target Flowable history tables (100k+ row scale).
-- Flyway runs before Flowable initialises act_* tables on a fresh DB, so each
-- statement is wrapped in a DO block that silently skips on undefined_table (42P01).
-- AnalyticsIndexInitializer creates the same indexes at ApplicationReadyEvent time
-- once Flowable has set up its schema.

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_end
        ON act_hi_procinst (tenant_id_, end_time_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_delete
        ON act_hi_procinst (tenant_id_, delete_reason_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_start
        ON act_hi_procinst (tenant_id_, start_time_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_taskdef
        ON act_hi_taskinst (tenant_id_, task_def_key_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_end
        ON act_hi_taskinst (tenant_id_, end_time_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_due
        ON act_hi_taskinst (tenant_id_, due_date_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
    CREATE INDEX IF NOT EXISTS idx_hi_actinst_proc_act
        ON act_hi_actinst (proc_inst_id_, act_type_);
EXCEPTION WHEN undefined_table THEN NULL;
END $$;
