package com.werkflow.admin.repository.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.HealthStatus;
import com.werkflow.admin.entity.serviceregistry.ServiceRegistry;
import com.werkflow.admin.entity.serviceregistry.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ServiceRegistry entity
 */
@Repository
public interface ServiceRegistryRepository extends JpaRepository<ServiceRegistry, UUID> {

    /**
     * Find service by its unique service name
     * @param serviceName The service name to search for
     * @return Optional containing the service if found
     */
    Optional<ServiceRegistry> findByServiceName(String serviceName);

    /**
     * Find all active services
     * @return List of active services
     */
    List<ServiceRegistry> findByActiveTrue();

    /**
     * Find all services by service type
     * @param serviceType The service type
     * @return List of services of the specified type
     */
    List<ServiceRegistry> findByServiceType(ServiceType serviceType);

    /**
     * Find all services by health status
     * @param healthStatus The health status
     * @return List of services with the specified health status
     */
    List<ServiceRegistry> findByHealthStatus(HealthStatus healthStatus);

    /**
     * Find all services owned by a specific user
     * @param ownerId The owner user ID
     * @return List of services owned by the user
     */
    List<ServiceRegistry> findByOwnerId(Long ownerId);

    /**
     * Check if a service with the given name exists
     * @param serviceName The service name to check
     * @return true if exists, false otherwise
     */
    boolean existsByServiceName(String serviceName);

    /**
     * Find all active services with pagination
     * @param pageable Pagination information
     * @return Page of active services
     */
    Page<ServiceRegistry> findByActiveTrue(Pageable pageable);

    /**
     * Find services by tags (at least one tag matches)
     * @param tags List of tags to search for
     * @return List of services that have at least one of the specified tags
     */
    @Query("SELECT DISTINCT s FROM ServiceRegistry s JOIN s.tags t WHERE t IN :tags")
    List<ServiceRegistry> findByTagsIn(@Param("tags") List<String> tags);

    /**
     * Find services by service type and active status
     * @param serviceType The service type
     * @param active The active status
     * @return List of services matching criteria
     */
    List<ServiceRegistry> findByServiceTypeAndActive(ServiceType serviceType, Boolean active);

    /**
     * Search services by name or display name (case-insensitive)
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of matching services
     */
    @Query("SELECT s FROM ServiceRegistry s WHERE " +
           "LOWER(s.serviceName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<ServiceRegistry> searchByNameOrDisplayName(@Param("searchTerm") String searchTerm, Pageable pageable);
}
