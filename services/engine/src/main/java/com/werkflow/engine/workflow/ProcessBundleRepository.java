package com.werkflow.engine.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessBundleRepository extends JpaRepository<ProcessBundle, UUID> {

    /** Highest bundle version recorded for a process in a tenant, or 0 if none. */
    @Query("SELECT COALESCE(MAX(b.bundleVersion), 0) FROM ProcessBundle b "
            + "WHERE b.tenantId = :tenantId AND b.processKey = :processKey")
    int findMaxBundleVersion(@Param("tenantId") String tenantId, @Param("processKey") String processKey);

    Optional<ProcessBundle> findByTenantIdAndProcessKeyAndBundleVersion(
            String tenantId, String processKey, int bundleVersion);
}
