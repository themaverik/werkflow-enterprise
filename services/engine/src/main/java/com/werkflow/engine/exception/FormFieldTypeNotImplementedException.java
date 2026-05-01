package com.werkflow.engine.exception;

/**
 * Thrown when a form submission contains a Category C (service-type) field
 * that is not yet supported for data submission (e.g. dynamiclist).
 * Maps to HTTP 501 Not Implemented.
 */
public class FormFieldTypeNotImplementedException extends RuntimeException {

    public FormFieldTypeNotImplementedException(String fieldType) {
        super("Form field type '" + fieldType + "' is not yet supported for data submission");
    }
}
