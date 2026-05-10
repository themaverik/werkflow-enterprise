package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.designtime.platform.dto.VisibleProcessEntry;
import com.werkflow.admin.designtime.platform.service.VisibilityFilterService.VisibilitySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Projects the set of process definitions visible to a user under ADR-010 §3 visibility rules.
 *
 * <p>The {@code process_draft} table lives in the Flowable engine schema ({@code flowable.process_draft})
 * and has no tenant_id column in v1 — it is global to the engine. Visibility is therefore
 * determined purely by {@code department_code}:
 * <ul>
 *   <li>Unrestricted (admin / manager-ALL_DEPTS): all rows returned without a department filter.</li>
 *   <li>Department-scoped: rows where {@code department_code = user's dept OR department_code IS NULL}
 *       (null = globally visible to every tenant user).</li>
 * </ul>
 *
 * <p>A cross-schema JDBC query is used, consistent with the {@link TagProjector} pattern, because
 * the engine schema and admin schema share the same Postgres instance in v1. If schemas are
 * separated in a future milestone, replace these queries with engine REST API calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessVisibilityProjector {

    private final JdbcTemplate jdbcTemplate;

    private static final String SQL_ALL =
            "SELECT process_key, name, department_code FROM flowable.process_draft";

    private static final String SQL_SCOPED =
            "SELECT process_key, name, department_code FROM flowable.process_draft " +
            "WHERE department_code IS NULL OR department_code = ?";

    /**
     * Returns process definitions visible to the user per ADR-010 §3.
     *
     * <p>When the spec is unrestricted, all drafts are returned.
     * When department-scoped, only globally-visible (null dept) and the user's own department
     * drafts are returned.
     *
     * @param spec the visibility spec computed from the JWT by {@link VisibilityFilterService}
     * @return ordered list of visible process entries; empty list on any DB error
     */
    public List<VisibleProcessEntry> listVisible(VisibilitySpec spec) {
        try {
            if (spec.isUnrestricted()) {
                log.debug("ProcessVisibilityProjector: unrestricted — returning all process drafts");
                return jdbcTemplate.query(
                        SQL_ALL,
                        (rs, row) -> new VisibleProcessEntry(
                                rs.getString("process_key"),
                                rs.getString("name"),
                                rs.getString("department_code")
                        )
                );
            }

            String deptCode = spec.userDept();
            log.debug("ProcessVisibilityProjector: department-scoped — dept={}", deptCode);
            return jdbcTemplate.query(
                    SQL_SCOPED,
                    (rs, row) -> new VisibleProcessEntry(
                            rs.getString("process_key"),
                            rs.getString("name"),
                            rs.getString("department_code")
                    ),
                    deptCode
            );
        } catch (Exception e) {
            log.warn("ProcessVisibilityProjector: could not query process_draft — {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
