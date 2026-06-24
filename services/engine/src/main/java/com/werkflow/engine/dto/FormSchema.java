package com.werkflow.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a Form Schema in the Werkflow system.
 * Contains the form-js schema definition and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSchema {

    /**
     * Unique identifier for the form schema
     */
    private UUID id;

    /**
     * Form key - unique identifier for the form (e.g., "capex-request-form")
     * Serialized as both "formKey" and "key" for frontend compatibility.
     */
    private String formKey;

    /**
     * Human-readable name of the form (e.g., "Capital Expenditure Request Form")
     */
    private String name;

    /**
     * Version number for schema evolution
     */
    private Integer version;

    /**
     * The complete form-js schema definition in JSON format
     */
    private JsonNode schemaJson;

    /**
     * Returns formKey as "key" for frontend compatibility
     */
    @JsonProperty("key")
    public String getKey() {
        return formKey;
    }

    /**
     * Returns schemaJson as stringified "formJson" for frontend compatibility
     */
    @JsonProperty("formJson")
    public String getFormJson() {
        return schemaJson != null ? schemaJson.toString() : null;
    }

    /**
     * Human-readable description of the form
     */
    private String description;

    /**
     * Type of form: PROCESS_START, TASK_FORM, APPROVAL, CUSTOM
     */
    private FormType formType;

    /**
     * Whether this version is currently active
     */
    private Boolean isActive;

    /**
     * Timestamp when the schema was created
     */
    private Instant createdAt;

    /**
     * Timestamp when the schema was last updated
     */
    private Instant updatedAt;

    /**
     * User who created the schema
     */
    private String createdBy;

    /**
     * User who last updated the schema
     */
    private String updatedBy;

    /**
     * Department that owns this form (has edit/delete rights)
     */
    private String owningDepartment;

    /**
     * Department of the user who originally created this form
     */
    private String createdByDepartment;

    /**
     * Tenant that owns this form schema row (strict scoping — no fallback to 'default').
     */
    private String tenantId;

    /**
     * Enumeration for form types
     */
    public enum FormType {
        PROCESS_START,
        TASK_FORM,
        APPROVAL,
        CUSTOM
    }
}
