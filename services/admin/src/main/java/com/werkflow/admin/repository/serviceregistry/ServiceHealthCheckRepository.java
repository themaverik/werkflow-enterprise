package com.werkflow.admin.repository.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
import com.werkflow.admin.entity.serviceregistry.HealthStatus;
import com.werkflow.admin.entity.serviceregistry.ServiceHealthCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ServiceHealthCheck entity
 */
@Repository
public interface ServiceHealthCheckRepository extends JpaRepository<ServiceHealthCheck, UUID> {

    /**
     * Find all health checks for a specific service
     * @param serviceId The service ID
     * @return List of health checks for the service
     */
    List<ServiceHealthCheck> findByServiceId(UUID serviceId);

    /**
     * Find all health checks for a specific service and environment
     * @param serviceId The service ID
     * @param environment The environment
     * @return List of health checks for the service and environment
     */
    List<ServiceHealthCheck> findByServiceIdAndEnvironment(UUID serviceId, Environment environment);

    /**
     * Find recent health checks for a service with pagination
     * @param serviceId The service ID
     * @param pageable Pagination information
     * @return Page of health checks ordered by check time descending
     */
    @Query("SELECT h FROM ServiceHealthCheck h WHERE h.service.id = :serviceId ORDER BY h.checkedAt DESC")
    Page<ServiceHealthCheck> findRecentByServiceId(@Param("serviceId") UUID serviceId, Pageable pageable);

    /**
     * Find the most recent health check for a service and environment
     * @param serviceId The service ID
     * @param environment The environment
     * @return Optional containing the most recent health check if found
     */
    @Query("SELECT h FROM ServiceHealthCheck h WHERE " +
           "h.service.id = :serviceId AND " +
           "h.environment = :environment " +
           "ORDER BY h.checkedAt DESC " +
           "LIMIT 1")
    Optional<ServiceHealthCheck> findMostRecentByServiceIdAndEnvironment(
        @Param("serviceId") UUID serviceId,
        @Param("environment") Environment environment
    );

    /**
     * Find health checks by status
     * @param status The health status
     * @return List of health checks with the specified status
     */
    List<ServiceHealthCheck> findByStatus(HealthStatus status);

    /**
     * Find health checks within a date range
     * @param serviceId The service ID
     * @param startDate The start date
     * @param endDate The end date
     * @return List of health checks within the date range
     */
    @Query("SELECT h FROM ServiceHealthCheck h WHERE " +
           "h.service.id = :serviceId AND " +
           "h.checkedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY h.checkedAt DESC")
    List<ServiceHealthCheck> findByServiceIdAndCheckedAtBetween(
        @Param("serviceId") UUID serviceId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    /**
     * Calculate average response time for a service in a specific environment
     * @param serviceId The service ID
     * @param environment The environment
     * @param since Calculate average since this date
     * @return Average response time in milliseconds
     */
    @Query("SELECT AVG(h.responseTimeMs) FROM ServiceHealthCheck h WHERE " +
           "h.service.id = :serviceId AND " +
           "h.environment = :environment AND " +
           "h.checkedAt >= :since AND " +
           "h.responseTimeMs IS NOT NULL")
    Optional<Double> calculateAverageResponseTime(
        @Param("serviceId") UUID serviceId,
        @Param("environment") Environment environment,
        @Param("since") OffsetDateTime since
    );
}
