package com.werkflow.engine.workflow;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Deploy-time indicator flags for a specific process definition version.
 * Persisted once per {@code processDefinitionId} at deploy time so that the
 * /processes list endpoint can read two booleans without re-scanning BPMN XML.
 *
 * <p>A missing row (LEFT JOIN) means both flags are false.
 */
@Entity
@Table(name = "process_indicators")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessIndicator {

    @Id
    @Column(name = "process_definition_id", nullable = false, updatable = false)
    private String processDefinitionId;

    @Column(name = "has_dmn", nullable = false)
    private boolean hasDmn;

    @Column(name = "has_connector", nullable = false)
    private boolean hasConnector;

    @Column(name = "has_notification", nullable = false)
    private boolean hasNotification;

    @CreationTimestamp
    @Column(name = "computed_at", updatable = false)
    private Instant computedAt;
}
