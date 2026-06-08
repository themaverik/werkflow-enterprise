package com.werkflow.engine.workflow;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "process_draft",
        uniqueConstraints = @UniqueConstraint(columnNames = {"process_key", "tenant_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "bpmn_xml", nullable = false, columnDefinition = "TEXT")
    private String bpmnXml;

    /** Department code for visibility scoping (ADR-010 — not routing). */
    @Column(name = "department_code")
    private String departmentCode;

    /** Category code from the admin-service category catalog. */
    @Column(name = "category_code")
    private String categoryCode;

    /** Free-form tags for search/filter (TEXT[] column added by V5 engine migration). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    @Builder.Default
    private List<String> tags = List.of();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
