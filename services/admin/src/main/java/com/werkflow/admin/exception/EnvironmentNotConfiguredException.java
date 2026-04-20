package com.werkflow.admin.exception;

/**
 * Exception thrown when a service does not have a URL configured for the requested environment
 */
public class EnvironmentNotConfiguredException extends RuntimeException {

    public EnvironmentNotConfiguredException(String serviceName, String environment) {
        super("Service '" + serviceName + "' does not have a URL configured for environment: " + environment);
    }

    public EnvironmentNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
