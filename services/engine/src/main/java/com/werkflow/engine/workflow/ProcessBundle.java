package com.werkflow.engine.workflow;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Werkflow-side index of a deployed process bundle (ADR-026 Phase 1): a BPMN and
 * its referenced DMNs deployed under one shared {@code parentDeploymentId}.
 * {@code bundleVersion} is monotonic per {@code (tenantId, processKey)}.
 */
@Entity
@Table(name = "process_bundle")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "process_key", nullable = false, updatable = false)
    private String processKey;

    @Column(name = "bundle_version", nullable = false, updatable = false)
    private int bundleVersion;

    @Column(name = "parent_deployment_id", nullable = false, updatable = false)
    private String parentDeploymentId;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
