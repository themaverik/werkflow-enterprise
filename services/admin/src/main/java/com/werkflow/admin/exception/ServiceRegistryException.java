package com.werkflow.admin.exception;

/**
 * Generic exception for service registry operations
 */
public class ServiceRegistryException extends RuntimeException {

    public ServiceRegistryException(String message) {
        super(message);
    }

    public ServiceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
