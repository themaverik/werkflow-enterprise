-- M6 Group A: analytics performance indexes.
-- All queries target Flowable history tables (100k+ row scale).
-- Guards: CREATE INDEX IF NOT EXISTS is idempotent.

-- ACT_HI_PROCINST: process execution stats queries
CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_end
    ON act_hi_procinst (tenant_id_, end_time_);

CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_delete
    ON act_hi_procinst (tenant_id_, delete_reason_);

CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_start
    ON act_hi_procinst (tenant_id_, start_time_);

-- ACT_HI_TASKINST: task metrics queries (cycle time, bottleneck, SLA)
CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_taskdef
    ON act_hi_taskinst (tenant_id_, task_def_key_);

CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_end
    ON act_hi_taskinst (tenant_id_, end_time_);

CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_due
    ON act_hi_taskinst (tenant_id_, due_date_);

-- ACT_HI_ACTINST: history timeline queries
CREATE INDEX IF NOT EXISTS idx_hi_actinst_proc_act
    ON act_hi_actinst (proc_inst_id_, act_type_);
