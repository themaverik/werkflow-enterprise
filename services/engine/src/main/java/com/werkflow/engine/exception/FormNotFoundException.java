package com.werkflow.engine.exception;

/**
 * Exception thrown when a form schema is not found in the system.
 */
public class FormNotFoundException extends RuntimeException {

    public FormNotFoundException(String formKey) {
        super("Form not found with key: " + formKey);
    }

    public FormNotFoundException(String formKey, Integer version) {
        super("Form not found with key: " + formKey + " and version: " + version);
    }
}
