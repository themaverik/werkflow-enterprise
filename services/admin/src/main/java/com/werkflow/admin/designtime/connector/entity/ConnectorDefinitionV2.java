package com.werkflow.admin.designtime.connector.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistent store for a versioned ConnectorDefinition envelope (M4.5).
 * The full definition JSON is stored as JSONB; the structural fields (key, version,
 * tenant_id) are extracted columns for indexed querying and uniqueness enforcement.
 *
 * <p>The UNIQUE constraint on (key, version, tenant_id) means a given connector
 * version can be registered once per tenant and is stable once published.</p>
 */
@Entity
@Table(name = "connector_definition_v2",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_connector_definition",
           columnNames = {"key", "version", "tenant_id"}))
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ConnectorDefinitionV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable kebab-case connector identifier — matches metadata.key in the JSON envelope. */
    @Column(nullable = false, length = 255)
    private String key;

    /** SemVer string — matches metadata.version. */
    @Column(nullable = false, length = 50)
    private String version;

    /** Tenant scope — matches the registered tenant. */
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /** Full ConnectorDefinition JSON — the canonical source of truth for this connector version. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
