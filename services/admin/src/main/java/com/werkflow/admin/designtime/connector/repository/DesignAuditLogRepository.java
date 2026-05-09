package com.werkflow.admin.designtime.connector.repository;

import com.werkflow.admin.designtime.connector.entity.DesignAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for DTDS call audit records. Write-only from the service perspective;
 * reads are exposed via admin reporting endpoints (future milestone).
 */
public interface DesignAuditLogRepository extends JpaRepository<DesignAuditLog, Long> {
}
