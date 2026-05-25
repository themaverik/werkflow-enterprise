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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FormSchemaService {

    private final JdbcTemplate jdbcTemplate;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;

    /**
     * Load form schema by form key (latest active version)
     * @param formKey The form key identifier
     * @return FormSchema
     * @throws FormNotFoundException if form is not found
     */
    @Cacheable(value = "formSchemas", key = "#formKey")
    public FormSchema loadFormSchema(String formKey) {
        log.info("Loading latest form schema for key: {}", formKey);

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department
                FROM form_schemas
                WHERE form_key = ? AND is_active = true
                ORDER BY version DESC
                LIMIT 1
                """;

        List<FormSchema> schemas = jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey);

        if (schemas.isEmpty()) {
            throw new FormNotFoundException(formKey);
        }

        return schemas.get(0);
    }

    /**
     * Load specific version of form schema
     * @param formKey The form key identifier
     * @param version The version number
     * @return FormSchema
     * @throws FormNotFoundException if form is not found
     */
    @Cacheable(value = "formSchemas", key = "#formKey + '_v' + #version")
    public FormSchema loadFormSchema(String formKey, Integer version) {
        log.info("Loading form schema for key: {} version: {}", formKey, version);

        // Deliberately no is_active filter: a bundle-pinned (ADR-026) or rolled-back version
        // must still resolve for in-flight instances even after it was later deactivated.
        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department
                FROM form_schemas
                WHERE form_key = ? AND version = ?
                """;

        List<FormSchema> schemas = jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey, version);

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
     * @return the resolved FormSchema
     * @throws FormNotFoundException if neither the pinned version nor the latest active form exists
     */
    public FormSchema loadFormSchemaByRef(String formKeyRef) {
        if (formKeyRef != null) {
            int at = formKeyRef.lastIndexOf('@');
            if (at > 0 && at < formKeyRef.length() - 1) {
                try {
                    return loadFormSchema(formKeyRef.substring(0, at),
                            Integer.valueOf(formKeyRef.substring(at + 1)));
                } catch (NumberFormatException notAVersionPin) {
                    // '@' is part of the key, not a version suffix — fall through to latest.
                }
            }
        }
        return loadFormSchema(formKeyRef);
    }

    /**
     * Save new form schema
     * @param formKey The form key identifier
     * @param schemaJson The form schema JSON
     * @param description Description of the form
     * @param formType Type of form
     * @param createdBy User creating the form
     * @return Created FormSchema
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema saveFormSchema(String formKey, JsonNode schemaJson, String description,
                                      FormSchema.FormType formType, String createdBy,
                                      String owningDepartment, String createdByDepartment) {
        log.info("Saving form schema for key: {}", formKey);

        if (formKey != null && formKey.indexOf('@') >= 0) {
            // '@' is reserved for the ADR-026 "formKey@version" pin; a key containing it would
            // be misparsed by loadFormSchemaByRef.
            throw new IllegalArgumentException("formKey must not contain '@': " + formKey);
        }

        formSchemaValidator.validateFormSchema(schemaJson);

        Integer nextVersion = getNextVersion(formKey);

        String sql = """
                INSERT INTO form_schemas (id, form_key, name, version, schema_json, description, form_type,
                                          is_active, created_at, updated_at, created_by, updated_by,
                                          owning_department, created_by_department)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, true, ?, ?, ?, ?, ?, ?)
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
                    owningDepartment, createdByDepartment
            );

            log.info("Saved form schema: {} version: {}", formKey, nextVersion);
            return loadFormSchema(formKey, nextVersion);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize schema JSON", e);
            throw new FormValidationException("Invalid JSON schema format");
        }
    }

    // Backward-compatible overload used internally (carries existing custody forward)
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema saveFormSchema(String formKey, JsonNode schemaJson, String description,
                                      FormSchema.FormType formType, String createdBy) {
        return saveFormSchema(formKey, schemaJson, description, formType, createdBy, null, null);
    }

    /**
     * Update existing form schema (creates new version).
     * Enforces department custody: only a manager-or-above in the owning department may update.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema updateFormSchema(String formKey, JsonNode schemaJson, String description,
                                        JwtUserContext updater) {
        log.info("Updating form schema for key: {} by user: {}", formKey, updater.getUserId());

        formSchemaValidator.validateFormSchema(schemaJson);

        FormSchema currentSchema = loadFormSchema(formKey);
        assertCustody(currentSchema, updater);

        String deactivateSql = """
                UPDATE form_schemas
                SET is_active = false, updated_at = ?, updated_by = ?
                WHERE form_key = ? AND is_active = true
                """;
        jdbcTemplate.update(deactivateSql, Timestamp.from(Instant.now()), updater.getUserId(), formKey);

        // Carry forward custody from the existing schema
        return saveFormSchema(formKey, schemaJson, description, currentSchema.getFormType(),
                updater.getUserId(),
                currentSchema.getOwningDepartment(),
                currentSchema.getCreatedByDepartment());
    }

    /**
     * Rollback a form to a specific previous version (re-activates that version).
     * Enforces department custody.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public FormSchema rollbackFormSchema(String formKey, Integer targetVersion, JwtUserContext updater) {
        log.info("Rolling back form: {} to version: {} by user: {}", formKey, targetVersion, updater.getUserId());

        FormSchema current = loadFormSchema(formKey);
        assertCustody(current, updater);

        FormSchema target = loadFormSchema(formKey, targetVersion);

        // Deactivate all versions
        jdbcTemplate.update("""
                UPDATE form_schemas SET is_active = false, updated_at = ?, updated_by = ?
                WHERE form_key = ?
                """, Timestamp.from(Instant.now()), updater.getUserId(), formKey);

        // Re-activate target version
        jdbcTemplate.update("""
                UPDATE form_schemas SET is_active = true, updated_at = ?, updated_by = ?
                WHERE form_key = ? AND version = ?
                """, Timestamp.from(Instant.now()), updater.getUserId(), formKey, targetVersion);

        log.info("Rolled back form: {} to version: {}", formKey, targetVersion);
        return loadFormSchema(formKey, targetVersion);
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
     * Get list of all active forms
     * @return List of FormSchema
     */
    public List<FormSchema> getFormsList() {
        log.info("Getting list of all forms");

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department
                FROM (
                    SELECT id, form_key, name, version, schema_json, description, form_type,
                           is_active, created_at, updated_at, created_by, updated_by,
                           owning_department, created_by_department,
                           ROW_NUMBER() OVER (PARTITION BY form_key ORDER BY version DESC) as rn
                    FROM form_schemas
                    WHERE is_active = true
                ) t
                WHERE rn = 1
                ORDER BY form_key
                """;

        return jdbcTemplate.query(sql, new FormSchemaRowMapper());
    }

    /**
     * Get form version history
     * @param formKey The form key identifier
     * @return List of all versions
     */
    public List<FormSchema> getFormHistory(String formKey) {
        log.info("Getting version history for form: {}", formKey);

        String sql = """
                SELECT id, form_key, name, version, schema_json, description, form_type,
                       is_active, created_at, updated_at, created_by, updated_by,
                       owning_department, created_by_department
                FROM form_schemas
                WHERE form_key = ?
                ORDER BY version DESC
                """;

        return jdbcTemplate.query(sql, new FormSchemaRowMapper(), formKey);
    }

    /**
     * Archive form (deactivate all versions)
     * @param formKey The form key to archive
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void archiveForm(String formKey) {
        log.info("Archiving form: {}", formKey);

        String sql = """
                UPDATE form_schemas
                SET is_active = false, updated_at = ?
                WHERE form_key = ?
                """;

        int updated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), formKey);

        if (updated == 0) {
            throw new FormNotFoundException(formKey);
        }

        log.info("Archived {} versions of form: {}", updated, formKey);
    }

    /**
     * Delete form schema (soft delete by deactivating)
     * @param formKey The form key to delete
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void deleteForm(String formKey) {
        archiveForm(formKey);
    }

    /**
     * Delete form schema with custody enforcement.
     */
    @Transactional
    @CacheEvict(value = "formSchemas", allEntries = true)
    public void deleteFormWithCustody(String formKey, JwtUserContext user) {
        FormSchema current = loadFormSchema(formKey);
        assertCustody(current, user);
        archiveForm(formKey);
    }

    /**
     * Validate form schema
     * @param schemaJson The schema to validate
     * @return true if valid
     * @throws FormValidationException if invalid
     */
    public boolean validateFormSchema(JsonNode schemaJson) {
        formSchemaValidator.validateFormSchema(schemaJson);
        return true;
    }

    /**
     * Get next version number for a form
     */
    private Integer getNextVersion(String formKey) {
        String sql = """
                SELECT COALESCE(MAX(version), 0) + 1
                FROM form_schemas
                WHERE form_key = ?
                """;

        Integer nextVersion = jdbcTemplate.queryForObject(sql, Integer.class, formKey);
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
                        .build();
            } catch (JsonProcessingException e) {
                log.error("Failed to parse schema JSON", e);
                throw new SQLException("Failed to parse schema JSON", e);
            }
        }
    }
}
