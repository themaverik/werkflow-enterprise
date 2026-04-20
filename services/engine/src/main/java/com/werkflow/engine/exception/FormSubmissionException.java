package com.werkflow.engine.exception;

/**
 * Exception thrown when form submission fails.
 */
public class FormSubmissionException extends RuntimeException {

    public FormSubmissionException(String message) {
        super(message);
    }

    public FormSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
