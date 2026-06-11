package com.werkflow.admin.designtime.connector.repository;

import com.werkflow.admin.designtime.connector.entity.DesignAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for DTDS call audit records. Write-only from the service perspective;
 * reads are exposed via admin reporting endpoints (future milestone).
 *
 * <p>Use {@link #findByTenantId} for all read access — never call the inherited
 * {@code findAll()} directly, as it crosses tenant boundaries.
 */
public interface DesignAuditLogRepository extends JpaRepository<DesignAuditLog, Long> {

    List<DesignAuditLog> findByTenantId(String tenantId);

    @Override
    default List<DesignAuditLog> findAll() {
        throw new UnsupportedOperationException(
            "findAll() is prohibited — use findByTenantId(tenantId) to scope reads to a single tenant");
    }
}
