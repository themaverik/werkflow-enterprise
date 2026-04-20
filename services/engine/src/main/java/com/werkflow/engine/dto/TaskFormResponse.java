package com.werkflow.engine.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for task form retrieval.
 * Contains the form schema and initial variable values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskFormResponse {

    /**
     * Form key identifier
     */
    private String formKey;

    /**
     * Version of the form schema
     */
    private Integer version;

    /**
     * Complete form-js schema definition
     */
    private JsonNode schema;

    /**
     * Initial form values from process variables
     */
    private Map<String, Object> variables;

    /**
     * Task ID this form is associated with
     */
    private String taskId;

    /**
     * Process instance ID
     */
    private String processInstanceId;

    /**
     * Form description
     */
    private String description;

    /**
     * Form type
     */
    private String formType;
}
