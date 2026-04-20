package com.werkflow.admin.exception;

/**
 * Exception thrown when attempting to register a service that already exists
 */
public class DuplicateServiceException extends RuntimeException {

    public DuplicateServiceException(String serviceName) {
        super("Service already exists with name: " + serviceName);
    }

    public DuplicateServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
