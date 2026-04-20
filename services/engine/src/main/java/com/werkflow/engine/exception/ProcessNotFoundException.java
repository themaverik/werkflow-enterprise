package com.werkflow.engine.exception;

/**
 * Exception thrown when a process instance is not found
 * Used in process monitoring APIs when querying by ID or business key
 */
public class ProcessNotFoundException extends RuntimeException {

    public ProcessNotFoundException(String message) {
        super(message);
    }

    public ProcessNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
