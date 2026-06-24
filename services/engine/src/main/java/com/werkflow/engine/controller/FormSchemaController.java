package com.werkflow.engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.service.FormSchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing form schemas.
 * Provides endpoints for CRUD operations on form-js schemas.
 *
 * <p>All endpoints (including reads) are tenant-scoped (D2): the tenant is resolved
 * from the JWT via {@link #extractTenant(Authentication)}.
 */
@RestController
@RequestMapping({"/forms", "/api/forms", "/api/v1/form-schemas"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Form Schemas", description = "Form schema management API")
public class FormSchemaController {

    private final FormSchemaService formSchemaService;

    /**
     * Get list of all forms for the caller's tenant.
     */
    @GetMapping
    @Operation(summary = "Get all forms", description = "Retrieve list of all active form schemas")
    public ResponseEntity<List<FormSchema>> getAllForms(Authentication authentication) {
        log.info("Getting all forms");
        List<FormSchema> forms = formSchemaService.getFormsList(extractTenant(authentication));
        return ResponseEntity.ok(forms);
    }

    /**
     * Get form schema by key (latest version) within the caller's tenant.
     */
    @GetMapping("/{formKey}")
    @Operation(summary = "Get form by key", description = "Retrieve latest active version of form schema")
    public ResponseEntity<FormSchema> getFormByKey(
            @Parameter(description = "Form key identifier")
            @PathVariable String formKey,
            Authentication authentication) {
        log.info("Getting form by key: {}", formKey);
        FormSchema form = formSchemaService.loadFormSchema(formKey, extractTenant(authentication));
        return ResponseEntity.ok(form);
    }

    /**
     * Get specific version of form schema within the caller's tenant.
     */
    @GetMapping("/{formKey}/versions/{version}")
    @Operation(summary = "Get specific form version", description = "Retrieve specific version of form schema")
    public ResponseEntity<FormSchema> getFormByVersion(
            @Parameter(description = "Form key identifier")
            @PathVariable String formKey,
            @Parameter(description = "Version number")
            @PathVariable Integer version,
            Authentication authentication) {
        log.info("Getting form by key: {} version: {}", formKey, version);
        FormSchema form = formSchemaService.loadFormSchema(formKey, version, extractTenant(authentication));
        return ResponseEntity.ok(form);
    }

    /**
     * Get form version history within the caller's tenant.
     */
    @GetMapping("/{formKey}/versions")
    @Operation(summary = "Get form version history", description = "Retrieve all versions of a form schema")
    public ResponseEntity<List<FormSchema>> getFormVersions(
            @Parameter(description = "Form key identifier")
            @PathVariable String formKey,
            Authentication authentication) {
        log.info("Getting version history for form: {}", formKey);
        List<FormSchema> versions = formSchemaService.getFormHistory(formKey, extractTenant(authentication));
        return ResponseEntity.ok(versions);
    }

    /**
     * Create new form schema
     */
    @PostMapping
    @Operation(summary = "Create form", description = "Create a new form schema")
    public ResponseEntity<FormSchema> createForm(
            @RequestBody CreateFormRequest request,
            Authentication authentication) {

        JwtUserContext user = extractUserContext(authentication);
        log.info("Creating form: {} by user: {}", request.getFormKey(), user.getUserId());

        FormSchema form = formSchemaService.saveFormSchema(
                request.getFormKey(),
                request.getSchemaJson(),
                request.getDescription(),
                request.getFormType(),
                user.getUserId(),
                request.getOwningDepartment(),
                user.getDepartment(),
                user.getTenantCode()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(form);
    }

    /**
     * Update form schema (creates new version)
     */
    @PutMapping("/{formKey}")
    @Operation(summary = "Update form", description = "Update form schema (creates new version). Requires custody.")
    public ResponseEntity<FormSchema> updateForm(
            @Parameter(description = "Form key identifier")
            @PathVariable String formKey,
            @RequestBody UpdateFormRequest request,
            Authentication authentication) {

        JwtUserContext user = extractUserContext(authentication);
        log.info("Updating form: {} by user: {}", formKey, user.getUserId());

        FormSchema form = formSchemaService.updateFormSchema(
                formKey,
                request.getSchemaJson(),
                request.getDescription(),
                user
        );

        return ResponseEntity.ok(form);
    }

    /**
     * Delete/archive form — requires custody (manager in owning department or admin).
     */
    @DeleteMapping("/{formKey}")
    @Operation(summary = "Delete form", description = "Archive/deactivate form schema. Requires custody.")
    public ResponseEntity<Map<String, String>> deleteForm(
            @Parameter(description = "Form key identifier")
            @PathVariable String formKey,
            Authentication authentication) {
        JwtUserContext user = extractUserContext(authentication);
        log.info("Deleting form: {} by user: {}", formKey, user.getUserId());
        // Load current schema to perform custody check
        formSchemaService.loadFormSchema(formKey, user.getTenantCode()); // throws if not found
        // Custody check delegated to service
        formSchemaService.deleteFormWithCustody(formKey, user);
        return ResponseEntity.ok(Map.of("message", "Form archived successfully", "formKey", formKey));
    }

    /**
     * Rollback form to a specific previous version.
     */
    @PostMapping("/{formKey}/rollback/{version}")
    @Operation(summary = "Rollback form version", description = "Restore a previous version of a form schema. Requires custody.")
    public ResponseEntity<FormSchema> rollbackForm(
            @Parameter(description = "Form key identifier") @PathVariable String formKey,
            @Parameter(description = "Version to restore") @PathVariable Integer version,
            Authentication authentication) {
        JwtUserContext user = extractUserContext(authentication);
        log.info("Rolling back form: {} to version: {} by user: {}", formKey, version, user.getUserId());
        FormSchema form = formSchemaService.rollbackFormSchema(formKey, version, user);
        return ResponseEntity.ok(form);
    }

    /**
     * Validate form schema — no tenant scope required.
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate form schema", description = "Validate form-js schema structure")
    public ResponseEntity<Map<String, Object>> validateSchema(
            @RequestBody JsonNode schemaJson) {
        log.info("Validating form schema");
        boolean isValid = formSchemaService.validateFormSchema(schemaJson);
        return ResponseEntity.ok(Map.of("valid", isValid, "message", "Schema is valid"));
    }

    private JwtUserContext extractUserContext(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new JwtUserContext(jwt);
        }
        return JwtUserContext.builder().userId("system").build();
    }

    /**
     * Returns the tenant code for the authenticated caller (defaults to "default").
     */
    private String extractTenant(Authentication authentication) {
        return extractUserContext(authentication).getTenantCode();
    }

    /**
     * Request DTO for creating forms
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateFormRequest {
        private String formKey;
        private JsonNode schemaJson;
        private String description;
        private FormSchema.FormType formType;
        private String owningDepartment;
    }

    /**
     * Request DTO for updating forms
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateFormRequest {
        private JsonNode schemaJson;
        private String description;
    }
}
