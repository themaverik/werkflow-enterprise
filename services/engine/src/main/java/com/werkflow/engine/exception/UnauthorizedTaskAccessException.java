package com.werkflow.engine.exception;

/**
 * Exception thrown when a user attempts to access a task they are not authorized to view
 */
public class UnauthorizedTaskAccessException extends RuntimeException {

    /**
     * Create exception with custom message
     * @param message Description of the authorization failure
     */
    public UnauthorizedTaskAccessException(String message) {
        super(message);
    }

    /**
     * Create exception with custom message and cause
     * @param message Description of the authorization failure
     * @param cause Root cause exception
     */
    public UnauthorizedTaskAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
