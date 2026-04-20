package com.werkflow.admin.repository.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.HttpMethod;
import com.werkflow.admin.entity.serviceregistry.ServiceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ServiceEndpoint entity
 */
@Repository
public interface ServiceEndpointRepository extends JpaRepository<ServiceEndpoint, UUID> {

    /**
     * Find all endpoints for a specific service
     * @param serviceId The service ID
     * @return List of endpoints for the service
     */
    List<ServiceEndpoint> findByServiceId(UUID serviceId);

    /**
     * Find all active endpoints for a specific service
     * @param serviceId The service ID
     * @return List of active endpoints for the service
     */
    List<ServiceEndpoint> findByServiceIdAndActiveTrue(UUID serviceId);

    /**
     * Find endpoint by service ID, endpoint path, and HTTP method
     * @param serviceId The service ID
     * @param endpointPath The endpoint path
     * @param httpMethod The HTTP method
     * @return Optional containing the endpoint if found
     */
    Optional<ServiceEndpoint> findByServiceIdAndEndpointPathAndHttpMethod(
        UUID serviceId,
        String endpointPath,
        HttpMethod httpMethod
    );

    /**
     * Find all endpoints by HTTP method
     * @param httpMethod The HTTP method
     * @return List of endpoints with the specified HTTP method
     */
    List<ServiceEndpoint> findByHttpMethod(HttpMethod httpMethod);

    /**
     * Find all endpoints that require authentication
     * @param requiresAuth Whether authentication is required
     * @return List of endpoints matching the authentication requirement
     */
    List<ServiceEndpoint> findByRequiresAuth(Boolean requiresAuth);

    /**
     * Find all active endpoints
     * @return List of all active endpoints
     */
    List<ServiceEndpoint> findByActiveTrue();

    /**
     * Check if an endpoint exists for a service
     * @param serviceId The service ID
     * @param endpointPath The endpoint path
     * @param httpMethod The HTTP method
     * @return true if exists, false otherwise
     */
    boolean existsByServiceIdAndEndpointPathAndHttpMethod(
        UUID serviceId,
        String endpointPath,
        HttpMethod httpMethod
    );
}
