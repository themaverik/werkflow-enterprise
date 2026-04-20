package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for submitting a task form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmitRequest {

    /**
     * Form data as key-value pairs
     */
    private Map<String, Object> formData;

    /**
     * Optional: Variables to save to the process instance
     */
    private Map<String, Object> variables;

    /**
     * Optional: Outcome for gateway decisions
     */
    private String outcome;
}
