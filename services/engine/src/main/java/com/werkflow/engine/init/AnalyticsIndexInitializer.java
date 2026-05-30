package com.werkflow.engine.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates analytics indexes on Flowable history tables after the application is fully up.
 *
 * V4__analytics_indexes.sql targets act_hi_* tables that Flowable creates during
 * Spring context initialisation — after Flyway has already run. On a fresh DB the
 * Flyway migration silently no-ops (DO/EXCEPTION block). This listener fills the
 * gap by running the same CREATE INDEX IF NOT EXISTS statements once Flowable has
 * set up its schema, making the operation idempotent on subsequent restarts.
 */
@Slf4j
@Component
public class AnalyticsIndexInitializer {

    private static final List<String> INDEX_DDL = List.of(
        "CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_end ON act_hi_procinst (tenant_id_, end_time_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_delete ON act_hi_procinst (tenant_id_, delete_reason_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_procinst_tenant_start ON act_hi_procinst (tenant_id_, start_time_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_taskdef ON act_hi_taskinst (tenant_id_, task_def_key_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_end ON act_hi_taskinst (tenant_id_, end_time_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_taskinst_tenant_due ON act_hi_taskinst (tenant_id_, due_date_)",
        "CREATE INDEX IF NOT EXISTS idx_hi_actinst_proc_act ON act_hi_actinst (proc_inst_id_, act_type_)"
    );

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createAnalyticsIndexes() {
        log.debug("Creating analytics indexes on Flowable history tables");
        for (String sql : INDEX_DDL) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Analytics index skipped: {}", e.getMessage());
            }
        }
    }
}
