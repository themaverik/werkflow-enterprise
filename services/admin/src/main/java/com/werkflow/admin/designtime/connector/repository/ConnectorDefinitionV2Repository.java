package com.werkflow.admin.designtime.connector.repository;

import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for versioned ConnectorDefinition envelopes.
 * All reads are tenant-scoped; cross-tenant access is prevented at the service layer.
 */
public interface ConnectorDefinitionV2Repository extends JpaRepository<ConnectorDefinitionV2, UUID> {

    /** Lists all connector definitions for a tenant, paginated. */
    Page<ConnectorDefinitionV2> findByTenantId(String tenantId, Pageable pageable);

    /** Returns the latest (or only) version of a connector for a tenant by key.
     *  Use this when callers omit an explicit version. */
    Optional<ConnectorDefinitionV2> findFirstByKeyAndTenantIdOrderByCreatedAtDesc(
            String key, String tenantId);

    /** Exact match by key + version + tenant — used for cache and idempotency checks. */
    Optional<ConnectorDefinitionV2> findByKeyAndVersionAndTenantId(
            String key, String version, String tenantId);

    /** Checks existence for duplicate-key detection on registration. */
    boolean existsByKeyAndVersionAndTenantId(String key, String version, String tenantId);
}
