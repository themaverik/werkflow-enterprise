package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for form definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinitionResponse {

    /**
     * Unique identifier for the form
     */
    private String id;

    /**
     * Form key/identifier
     */
    private String key;

    /**
     * Form name
     */
    private String name;

    /**
     * Form description
     */
    private String description;

    /**
     * The form definition (JSON schema compatible with formio)
     */
    private Map<String, Object> definition;

    /**
     * Form type
     */
    private String type;

    /**
     * Version of the form
     */
    private Integer version;

    /**
     * Whether the form is active
     */
    private Boolean active;
}
