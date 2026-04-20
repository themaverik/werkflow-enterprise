package com.werkflow.engine.exception;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when form validation fails.
 */
public class FormValidationException extends RuntimeException {

    private final Map<String, List<String>> validationErrors;

    public FormValidationException(String message) {
        super(message);
        this.validationErrors = Map.of();
    }

    public FormValidationException(String message, Map<String, List<String>> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    public Map<String, List<String>> getValidationErrors() {
        return validationErrors;
    }
}
