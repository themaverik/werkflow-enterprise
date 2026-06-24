package com.werkflow.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.exception.FormValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing form schemas in the database.
 * Handles CRUD operations for form-js schemas stored in PostgreSQL.
 *
 * <p>All read and write paths are strictly tenant-scoped (D2). Every method takes an explicit
 * {@code tenantId} and filters {@code WHERE tenant_id = ?}. There is no fallback-to-default.
 * Callers must normalise null/blank tenant values to "default" before calling (see
 * {@link #normaliseTenant(String)}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FormSchemaService {

    private final JdbcTemplate jdbcTemplate;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;

    /**
     * Normalises a tenant value: null or blank becomes "default".
     */
    public static String normaliseTenant(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    /**
     * Load form schema by form key (latest active version) within a tenant.
     *
     * @param formKey  The form key identifier
     * @param tenantId Tenant scope (null/blank → "default")
     * @return FormSchema
     * @throws FormNotFoundException if form is not found for this tenant
     */
    @Cacheable(value = "formSchemas", key = "T(com.werkflow.engine.service.FormSchemaService).normaliseTenant(#tenantId) + ':' + #formKey")
    public FormSchema loadFormSchema(String formKey, String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Loading latest form schema for key: {} tenant: {}", formKey, tid);

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department, tenant_id
                FROM form_schemas
                WHERE form_key = ? AND is_active = true AND tenant_id = ?
                ORDER BY version DESC
                LIMIT 1
                """;

        List<FormSchema> schemas = jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey, tid);

        if (schemas.isEmpty()) {
            throw new FormNotFoundException(formKey);
        }

        return schemas.get(0);
    }

    /**
     * Load specific version of form schema within a tenant.
     *
     * @param formKey  The form key identifier
     * @param version  The version number
     * @param tenantId Tenant scope (null/blank → "default")
     * @return FormSchema
     * @throws FormNotFoundException if form is not found for this tenant
     */
    // Separator '@' (not '_v') matches the canonical "formKey@version" pin format and cannot
    // collide with the unversioned-key entry above: '@' is reserved and rejected in formKeys
    // (see saveFormSchema), so no formKey ending in "@<n>" can ever equal another key's
    // versioned cache entry.
    @Cacheable(value = "formSchemas", key = "T(com.werkflow.engine.service.FormSchemaService).normaliseTenant(#tenantId) + ':' + #formKey + '@' + #version")
    public FormSchema loadFormSchema(String formKey, Integer version, String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Loading form schema for key: {} version: {} tenant: {}", formKey, version, tid);

        // Deliberately no is_active filter: a bundle-pinned (ADR-026) or rolled-back version
        // must still resolve for in-flight instances even after it was later deactivated.
        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department, tenant_id
                FROM form_schemas
                WHERE form_key = ? AND version = ? AND tenant_id = ?
                """;

        List<FormSchema> schemas = jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey, version, tid);

        if (schemas.isEmpty()) {
            throw new FormNotFoundException(formKey, version);
        }

        return schemas.get(0);
    }

    /**
     * Resolves a form from a possibly version-pinned BPMN formKey (ADR-026 P2 / F1).
     * Accepts either a bare {@code "formKey"} (→ latest active version) or a pinned
     * {@code "formKey@version"} (→ that exact version). A trailing {@code @<non-numeric>}
     * is treated as part of the key, not a pin, so it degrades to a latest lookup.
     * Bundle deploy embeds the {@code @version} suffix so an in-flight instance resolves
     * the same form definition it was deployed with.
     *
     * @param formKeyRef a BPMN-authored formKey, optionally pinned with {@code @version}
     * @param tenantId   Tenant scope (null/blank → "default")
     * @return the resolved FormSchema
     * @throws FormNotFoundException if neither the pinned version nor the latest active form exists
     */
    public FormSchema loadFormSchemaByRef(String formKeyRef, String tenantId) {
        if (formKeyRef != null) {
            int at = formKeyRef.lastIndexOf('@');
            if (at > 0 && at < formKeyRef.length() - 1) {
                try {
                    return loadFormSchema(formKeyRef.substring(0, at),
                            Integer.valueOf(formKeyRef.substring(at + 1)),
                            tenantId);
                } catch (NumberFormatException notAVersionPin) {
                    // '@' is part of the key, not a version suffix — fall through to latest.
                }
            }
        }
        return loadFormSchema(formKeyRef, tenantId);
    }

    /**
     * Save new form schema for a tenant.
     *
     * @param formKey             The form key identifier
     * @param schemaJson          The form schema JSON
     * @param description         Description of the form
     * @param formType            Type of form
     * @param createdBy           User creating the form
     * @param owningDepartment    Department that owns the form
     * @param createdByDepartment Department of the creator
     * @param tenantId            Tenant scope (null/blank → "default")
     * @return Created FormSchema
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema saveFormSchema(String formKey, JsonNode schemaJson, String description,
                                      FormSchema.FormType formType, String createdBy,
                                      String owningDepartment, String createdByDepartment,
                                      String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Saving form schema for key: {} tenant: {}", formKey, tid);

        if (formKey != null && formKey.indexOf('@') >= 0) {
            // '@' is reserved for the ADR-026 "formKey@version" pin; a key containing it would
            // be misparsed by loadFormSchemaByRef.
            throw new IllegalArgumentException("formKey must not contain '@': " + formKey);
        }

        formSchemaValidator.validateFormSchema(schemaJson);

        Integer nextVersion = getNextVersion(formKey, tid);

        String sql = """
                INSERT INTO form_schemas (id, form_key, name, version, schema_json, description, form_type,
                                          is_active, created_at, updated_at, created_by, updated_by,
                                          owning_department, created_by_department, tenant_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, true, ?, ?, ?, ?, ?, ?, ?)
                """;

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        String name = java.util.Arrays.stream(formKey.split("-"))
                .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));

        try {
            String schemaJsonStr = objectMapper.writeValueAsString(schemaJson);

            jdbcTemplate.update(sql,
                    id, formKey, name, nextVersion, schemaJsonStr,
                    description, (formType != null ? formType : FormSchema.FormType.CUSTOM).name(),
                    Timestamp.from(now), Timestamp.from(now),
                    createdBy, createdBy,
                    owningDepartment, createdByDepartment,
                    tid
            );

            log.info("Saved form schema: {} version: {} tenant: {}", formKey, nextVersion, tid);
            return loadFormSchema(formKey, nextVersion, tid);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize schema JSON", e);
            throw new FormValidationException("Invalid JSON schema format");
        }
    }

    // Backward-compatible overload used internally (carries existing custody forward)
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema saveFormSchema(String formKey, JsonNode schemaJson, String description,
                                      FormSchema.FormType formType, String createdBy,
                                      String tenantId) {
        return saveFormSchema(formKey, schemaJson, description, formType, createdBy, null, null, tenantId);
    }

    /**
     * Update existing form schema (creates new version) within a tenant.
     * Enforces department custody: only a manager-or-above in the owning department may update.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema updateFormSchema(String formKey, JsonNode schemaJson, String description,
                                        JwtUserContext updater) {
        String tid = normaliseTenant(updater.getTenantCode());
        log.info("Updating form schema for key: {} by user: {} tenant: {}", formKey, updater.getUserId(), tid);

        formSchemaValidator.validateFormSchema(schemaJson);

        FormSchema currentSchema = loadFormSchema(formKey, tid);
        assertCustody(currentSchema, updater);

        String deactivateSql = """
                UPDATE form_schemas
                SET is_active = false, updated_at = ?, updated_by = ?
                WHERE form_key = ? AND is_active = true AND tenant_id = ?
                """;
        jdbcTemplate.update(deactivateSql, Timestamp.from(Instant.now()), updater.getUserId(), formKey, tid);

        // Carry forward custody from the existing schema
        return saveFormSchema(formKey, schemaJson, description, currentSchema.getFormType(),
                updater.getUserId(),
                currentSchema.getOwningDepartment(),
                currentSchema.getCreatedByDepartment(),
                tid);
    }

    /**
     * Rollback a form to a specific previous version (re-activates that version) within a tenant.
     * Enforces department custody.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema rollbackFormSchema(String formKey, Integer targetVersion, JwtUserContext updater) {
        String tid = normaliseTenant(updater.getTenantCode());
        log.info("Rolling back form: {} to version: {} by user: {} tenant: {}", formKey, targetVersion, updater.getUserId(), tid);

        FormSchema current = loadFormSchema(formKey, tid);
        assertCustody(current, updater);

        // Existence guard: throws FormNotFoundException if the target version does not exist for this tenant.
        loadFormSchema(formKey, targetVersion, tid);

        // Deactivate all versions for this tenant
        jdbcTemplate.update("""
                UPDATE form_schemas SET is_active = false, updated_at = ?, updated_by = ?
                WHERE form_key = ? AND tenant_id = ?
                """, Timestamp.from(Instant.now()), updater.getUserId(), formKey, tid);

        // Re-activate target version for this tenant
        jdbcTemplate.update("""
                UPDATE form_schemas SET is_active = true, updated_at = ?, updated_by = ?
                WHERE form_key = ? AND version = ? AND tenant_id = ?
                """, Timestamp.from(Instant.now()), updater.getUserId(), formKey, targetVersion, tid);

        log.info("Rolled back form: {} to version: {} tenant: {}", formKey, targetVersion, tid);
        return loadFormSchema(formKey, targetVersion, tid);
    }

    /**
     * Assert that the given user has custody rights over the form.
     * Rules: user must have manager/admin role AND be in the owning department (if set).
     */
    private void assertCustody(FormSchema form, JwtUserContext user) {
        boolean isManagerOrAbove = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.equalsIgnoreCase("admin") ||
                               r.equalsIgnoreCase("super_admin") ||
                               r.equalsIgnoreCase("WORKFLOW_DESIGNER") ||
                               r.equalsIgnoreCase("WORKFLOW_ADMIN") ||
                               r.toLowerCase().contains("manager") ||
                               r.toLowerCase().contains("admin"));

        if (!isManagerOrAbove) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only managers and administrators can edit forms");
        }

        String owningDept = form.getOwningDepartment();
        if (owningDept != null && !owningDept.isBlank()) {
            boolean sameDept = owningDept.equalsIgnoreCase(user.getDepartment());
            boolean isSuperAdmin = user.getRoles() != null && user.getRoles().stream()
                    .anyMatch(r -> r.equalsIgnoreCase("super_admin") || r.equalsIgnoreCase("admin"));
            if (!sameDept && !isSuperAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only users in the '" + owningDept + "' department can edit this form");
            }
        }
    }

    /**
     * Get list of all active forms for a tenant.
     *
     * @param tenantId Tenant scope (null/blank → "default")
     * @return List of FormSchema
     */
    public List<FormSchema> getFormsList(String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Getting list of all forms for tenant: {}", tid);

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department, tenant_id
                FROM (
                    SELECT id, form_key, name, version, schema_json, description, form_type,
                           is_active, created_at, updated_at, created_by, updated_by,
                           owning_department, created_by_department, tenant_id,
                           ROW_NUMBER() OVER (PARTITION BY form_key ORDER BY version DESC) as rn
                    FROM form_schemas
                    WHERE is_active = true AND tenant_id = ?
                ) t
                WHERE rn = 1
                ORDER BY form_key
                """;

        return jdbcTemplate.query(sql, new FormSchemaRowMapper(), tid);
    }

    /**
     * Get form version history for a tenant.
     *
     * @param formKey  The form key identifier
     * @param tenantId Tenant scope (null/blank → "default")
     * @return List of all versions
     */
    public List<FormSchema> getFormHistory(String formKey, String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Getting version history for form: {} tenant: {}", formKey, tid);

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department, tenant_id
                FROM form_schemas
                WHERE form_key = ? AND tenant_id = ?
                ORDER BY version DESC
                """;

        return jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey, tid);
    }

    /**
     * Archive form (deactivate all versions) within a tenant.
     *
     * @param formKey  The form key to archive
     * @param tenantId Tenant scope (null/blank → "default")
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void archiveForm(String formKey, String tenantId) {
        String tid = normaliseTenant(tenantId);
        log.info("Archiving form: {} tenant: {}", formKey, tid);

        String sql = """
                UPDATE form_schemas
                SET is_active = false, updated_at = ?
                WHERE form_key = ? AND tenant_id = ?
                """;

        int updated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), formKey, tid);

        if (updated == 0) {
            throw new FormNotFoundException(formKey);
        }

        log.info("Archived {} versions of form: {} tenant: {}", updated, formKey, tid);
    }

    /**
     * Delete form schema (soft delete by deactivating) within a tenant.
     *
     * @param formKey  The form key to delete
     * @param tenantId Tenant scope (null/blank → "default")
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void deleteForm(String formKey, String tenantId) {
        archiveForm(formKey, tenantId);
    }

    /**
     * Delete form schema with custody enforcement within a tenant.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void deleteFormWithCustody(String formKey, JwtUserContext user) {
        String tid = normaliseTenant(user.getTenantCode());
        FormSchema current = loadFormSchema(formKey, tid);
        assertCustody(current, user);
        archiveForm(formKey, tid);
    }

    /**
     * Validate form schema.
     *
     * @param schemaJson The schema to validate
     * @return true if valid
     * @throws FormValidationException if invalid
     */
    public boolean validateFormSchema(JsonNode schemaJson) {
        formSchemaValidator.validateFormSchema(schemaJson);
        return true;
    }

    /**
     * Returns true if any version of the form exists for the given tenant, regardless of active status.
     * Used by seeding logic to prevent duplicate version creation when a form has been archived.
     *
     * @param formKey  The form key identifier
     * @param tenantId Tenant scope (null/blank → "default")
     * @return true if at least one row exists in form_schemas for this key and tenant
     */
    public boolean formExistsAnyVersion(String formKey, String tenantId) {
        String tid = normaliseTenant(tenantId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM form_schemas WHERE form_key = ? AND tenant_id = ?",
                Integer.class, formKey, tid);
        return count != null && count > 0;
    }

    /**
     * Get next version number for a form within a tenant.
     * Each tenant has an independent version sequence per form key.
     */
    private Integer getNextVersion(String formKey, String tenantId) {
        tenantId = normaliseTenant(tenantId);
        String sql = """
                SELECT COALESCE(MAX(version), 0) + 1
                FROM form_schemas
                WHERE form_key = ? AND tenant_id = ?
                """;

        Integer nextVersion = jdbcTemplate.queryForObject(sql, Integer.class, formKey, tenantId);
        return nextVersion != null ? nextVersion : 1;
    }

    /**
     * Row mapper for FormSchema
     */
    private class FormSchemaRowMapper implements RowMapper<FormSchema> {
        @Override
        public FormSchema mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String schemaJsonStr = rs.getString("schema_json");
                JsonNode schemaJson = objectMapper.readTree(schemaJsonStr);

                return FormSchema.builder()
                        .id(UUID.fromString(rs.getString("id")))
                        .formKey(rs.getString("form_key"))
                        .name(rs.getString("name"))
                        .version(rs.getInt("version"))
                        .schemaJson(schemaJson)
                        .description(rs.getString("description"))
                        .formType(FormSchema.FormType.valueOf(rs.getString("form_type")))
                        .isActive(rs.getBoolean("is_active"))
                        .createdAt(rs.getTimestamp("created_at").toInstant())
                        .updatedAt(rs.getTimestamp("updated_at").toInstant())
                        .createdBy(rs.getString("created_by"))
                        .updatedBy(rs.getString("updated_by"))
                        .owningDepartment(rs.getString("owning_department"))
                        .createdByDepartment(rs.getString("created_by_department"))
                        .tenantId(rs.getString("tenant_id"))
                        .build();
            } catch (JsonProcessingException e) {
                log.error("Failed to parse schema JSON", e);
                throw new SQLException("Failed to parse schema JSON", e);
            }
        }
    }
}
