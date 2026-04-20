package com.werkflow.admin.exception;

import java.util.UUID;

/**
 * Exception thrown when a service is not found in the registry
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(String serviceName) {
        super("Service not found with name: " + serviceName);
    }

    public ServiceNotFoundException(UUID serviceId) {
        super("Service not found with ID: " + serviceId);
    }

    public ServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
