package com.werkflow.admin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores the connection metadata for a tenant's registered JDBC datasource.
 *
 * <p>The password is stored as AES-256-GCM ciphertext in {@code encryptedPassword}.
 * {@link com.werkflow.common.security.EncryptionService} encrypts on write and
 * decrypts on read. The plaintext credential is never persisted or logged.</p>
 *
 * <p>Unique constraint on {@code (tenantId, ref)} ensures each tenant can register
 * a datasource reference once, and the ref slug is immutable after creation.</p>
 */
@Entity
@Table(
    name = "tenant_datasource",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_datasource_ref",
        columnNames = {"tenant_id", "ref"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TenantDatasource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    /**
     * Stable slug identifier for this datasource within the tenant.
     * Pattern: lowercase letters and digits, hyphens allowed, must start with a letter.
     * Immutable after creation — changing refs would break deployed connectors.
     */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "ref must be lowercase alphanumeric with hyphens, starting with a letter")
    @Column(name = "ref", nullable = false, length = 100)
    private String ref;

    @NotBlank
    @Column(name = "jdbc_url", nullable = false, columnDefinition = "TEXT")
    private String jdbcUrl;

    @NotBlank
    @Column(name = "driver_class_name", nullable = false, length = 200)
    private String driverClassName;

    @NotBlank
    @Column(name = "username", nullable = false, length = 200)
    private String username;

    /**
     * AES-256-GCM encrypted password, managed by
     * {@link com.werkflow.common.security.EncryptionService}.
     * The plaintext is only decrypted for engine-internal resolution
     * and is never returned to external callers.
     */
    @NotBlank
    @Column(name = "encrypted_password", nullable = false, length = 500)
    private String encryptedPassword;

    /** SQL dialect hint used by the engine to apply dialect-specific features. */
    @Column(name = "dialect", length = 50)
    private String dialect;

    @Column(name = "pool_min_size", nullable = false)
    private int poolMinSize = 1;

    @Column(name = "pool_max_size", nullable = false)
    private int poolMaxSize = 5;

    @Column(name = "connection_timeout_seconds", nullable = false)
    private int connectionTimeoutSeconds = 5;

    @Column(name = "idle_timeout_seconds", nullable = false)
    private int idleTimeoutSeconds = 600;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
