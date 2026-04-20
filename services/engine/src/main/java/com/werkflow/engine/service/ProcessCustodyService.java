package com.werkflow.engine.service;

import com.werkflow.engine.dto.JwtUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Manages department ownership (custody) for Flowable process definitions.
 * Custody determines who has edit and delete rights over a process definition.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessCustodyService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Record custody for a newly deployed process definition.
     */
    public void recordCustody(String processDefinitionKey, String owningDepartment,
                               String createdBy, String createdByDepartment) {
        log.info("Recording custody for process: {} department: {}", processDefinitionKey, owningDepartment);
        String sql = """
                INSERT INTO process_custody (process_definition_key, owning_department, created_by, created_by_department, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (process_definition_key) DO UPDATE
                SET owning_department = EXCLUDED.owning_department,
                    created_by = EXCLUDED.created_by,
                    created_by_department = EXCLUDED.created_by_department
                """;
        jdbcTemplate.update(sql, processDefinitionKey, owningDepartment, createdBy, createdByDepartment,
                Timestamp.from(Instant.now()));
    }

    /**
     * Look up the owning department for a process definition key.
     * Returns null if no custody record exists.
     */
    public String getOwningDepartment(String processDefinitionKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT owning_department FROM process_custody WHERE process_definition_key = ?",
                    String.class,
                    processDefinitionKey);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Assert that the given user has custody rights over this process definition.
     * Rules: manager-or-above AND (same department as owning dept OR super_admin/admin).
     */
    public void assertCustody(String processDefinitionKey, JwtUserContext user) {
        boolean isManagerOrAbove = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.equalsIgnoreCase("admin") ||
                               r.equalsIgnoreCase("super_admin") ||
                               r.toLowerCase().contains("manager") ||
                               r.toLowerCase().contains("admin"));

        if (!isManagerOrAbove) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only managers and administrators can modify process definitions");
        }

        String owningDept = getOwningDepartment(processDefinitionKey);
        if (owningDept != null && !owningDept.isBlank()) {
            boolean sameDept = owningDept.equalsIgnoreCase(user.getDepartment());
            boolean isSuperAdmin = user.getRoles() != null && user.getRoles().stream()
                    .anyMatch(r -> r.equalsIgnoreCase("super_admin") || r.equalsIgnoreCase("admin"));
            if (!sameDept && !isSuperAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only users in the '" + owningDept + "' department can modify this process");
            }
        }
    }
}
