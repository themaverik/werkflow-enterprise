package com.werkflow.admin.repository.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
import com.werkflow.admin.entity.serviceregistry.ServiceEnvironmentUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ServiceEnvironmentUrl entity
 */
@Repository
public interface ServiceEnvironmentUrlRepository extends JpaRepository<ServiceEnvironmentUrl, UUID> {

    /**
     * Find all URLs for a specific service
     * @param serviceId The service ID
     * @return List of environment URLs for the service
     */
    List<ServiceEnvironmentUrl> findByServiceId(UUID serviceId);

    /**
     * Find all URLs for a specific service and environment
     * @param serviceId The service ID
     * @param environment The environment
     * @return List of URLs for the service and environment
     */
    List<ServiceEnvironmentUrl> findByServiceIdAndEnvironment(UUID serviceId, Environment environment);

    /**
     * Find the highest priority active URL for a service in a specific environment
     * @param serviceId The service ID
     * @param environment The environment
     * @return Optional containing the URL if found
     */
    @Query("SELECT u FROM ServiceEnvironmentUrl u WHERE " +
           "u.service.id = :serviceId AND " +
           "u.environment = :environment AND " +
           "u.active = true " +
           "ORDER BY u.priority ASC")
    Optional<ServiceEnvironmentUrl> findFirstByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
        @Param("serviceId") UUID serviceId,
        @Param("environment") Environment environment
    );

    /**
     * Find all active URLs for a service and environment, ordered by priority
     * @param serviceId The service ID
     * @param environment The environment
     * @return List of active URLs ordered by priority
     */
    List<ServiceEnvironmentUrl> findByServiceIdAndEnvironmentAndActiveTrueOrderByPriorityAsc(
        UUID serviceId,
        Environment environment
    );

    /**
     * Find all URLs for a specific environment
     * @param environment The environment
     * @return List of URLs for the environment
     */
    List<ServiceEnvironmentUrl> findByEnvironment(Environment environment);

    /**
     * Find all active URLs
     * @return List of all active URLs
     */
    List<ServiceEnvironmentUrl> findByActiveTrue();

    /**
     * Check if a URL exists for a service and environment with a specific priority
     * @param serviceId The service ID
     * @param environment The environment
     * @param priority The priority
     * @return true if exists, false otherwise
     */
    boolean existsByServiceIdAndEnvironmentAndPriority(
        UUID serviceId,
        Environment environment,
        Integer priority
    );
}
