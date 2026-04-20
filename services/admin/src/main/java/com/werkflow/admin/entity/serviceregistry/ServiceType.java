package com.werkflow.admin.entity.serviceregistry;

/**
 * Enum representing the type of service in the registry
 */
public enum ServiceType {
    /**
     * Internal service within the Werkflow platform
     */
    INTERNAL,

    /**
     * External service from partner organizations
     */
    EXTERNAL,

    /**
     * Third-party service or API
     */
    THIRD_PARTY
}
