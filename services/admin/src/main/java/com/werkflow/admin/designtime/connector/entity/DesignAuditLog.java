package com.werkflow.admin.designtime.connector.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit record for every DTDS (Design-Time Data Service) API call.
 * Written on every request to /api/v1/design/connectors/** so that tenant
 * administrators can review designer activity without enabling full request logging.
 */
@Entity
@Table(name = "design_audit_log")
@Getter @Setter @NoArgsConstructor
public class DesignAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /** Keycloak preferred_username or subject of the calling principal. */
    @Column(length = 255)
    private String principal;

    /** The full endpoint path that was called, e.g. /api/v1/design/connectors/procurement/operations. */
    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(name = "connector_key", length = 255)
    private String connectorKey;

    @Column(name = "operation_id", length = 255)
    private String operationId;

    /** "input" or "output" — populated for schema/fields endpoints, null otherwise. */
    @Column(length = 10)
    private String direction;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;
}
