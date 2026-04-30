package com.werkflow.engine.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds supplementary indexes to Flowable runtime tables for common query patterns (ADR-M2-perf).
 *
 * Flowable's built-in schema does not include indexes for every application-level query predicate.
 * These indexes cover the high-frequency task lookup paths: assignee filtering, priority sorting,
 * due-date range queries, and tenant-scoped candidacy lookups.
 *
 * {@code CREATE INDEX IF NOT EXISTS} is idempotent — safe to run on every startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowableIndexCreator {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void createIndexes() {
        log.info("Ensuring Flowable performance indexes exist");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_task_assignee " +
                "ON act_ru_task (assignee_)");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_task_priority " +
                "ON act_ru_task (priority_)");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_task_due_date " +
                "ON act_ru_task (due_date_)");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_task_tenant_assignee " +
                "ON act_ru_task (tenant_id_, assignee_)");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_task_tenant_proc_def " +
                "ON act_ru_task (tenant_id_, proc_def_id_)");

        execute("CREATE INDEX IF NOT EXISTS idx_act_ru_identitylink_task_type " +
                "ON act_ru_identitylink (task_id_, type_, group_id_)");

        log.info("Flowable performance indexes verified");
    }

    private void execute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Could not create index (may already exist or table absent): {}", e.getMessage());
        }
    }
}
