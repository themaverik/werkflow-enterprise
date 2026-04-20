package com.werkflow.admin.entity.serviceregistry;

/**
 * Enum representing the health status of a service
 */
public enum HealthStatus {
    /**
     * Service is fully operational
     */
    HEALTHY,

    /**
     * Service is not responding or failing health checks
     */
    UNHEALTHY,

    /**
     * Health status has not been determined yet
     */
    UNKNOWN,

    /**
     * Service is operational but with degraded performance
     */
    DEGRADED
}
