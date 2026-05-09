package com.werkflow.admin.designtime.connector.service;

import com.werkflow.admin.designtime.connector.entity.DesignAuditLog;
import com.werkflow.admin.designtime.connector.repository.DesignAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes DTDS call audit records asynchronously so that audit logging
 * never adds latency to the caller.
 *
 * <p>Uses a separate transaction ({@code REQUIRES_NEW}) so that a failed
 * audit write never rolls back the calling read operation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignAuditService {

    private final DesignAuditLogRepository auditRepo;

    /**
     * Records a DTDS API call.  Runs asynchronously in a separate transaction.
     *
     * @param tenantId     tenant scope
     * @param principal    Keycloak username or subject
     * @param endpoint     the full request path
     * @param connectorKey may be null for list endpoints
     * @param operationId  may be null
     * @param direction    "input", "output", or null
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String tenantId,
            String principal,
            String endpoint,
            String connectorKey,
            String operationId,
            String direction) {
        try {
            DesignAuditLog log = new DesignAuditLog();
            log.setTenantId(tenantId);
            log.setPrincipal(principal);
            log.setEndpoint(endpoint);
            log.setConnectorKey(connectorKey);
            log.setOperationId(operationId);
            log.setDirection(direction);
            log.setCalledAt(LocalDateTime.now());
            auditRepo.save(log);
        } catch (Exception e) {
            // Audit failure must never propagate to the caller
            Slf4jLogger.warn("Design audit write failed — tenantId={} endpoint={}: {}",
                    tenantId, endpoint, e.getMessage());
        }
    }

    /** Micro-helper to avoid field-name clash with the @Slf4j-injected field. */
    private static final class Slf4jLogger {
        private static final org.slf4j.Logger L =
                org.slf4j.LoggerFactory.getLogger(DesignAuditService.class);

        static void warn(String fmt, Object... args) { L.warn(fmt, args); }
    }
}
