package com.werkflow.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * Metadata index for a tenant credential stored in OpenBao (M4.12 Phase B.2).
 *
 * <p>This entity holds <b>no secret material</b>. The value payload (e.g. Slack
 * bot tokens, SMTP passwords) lives at {@code vaultPath} in OpenBao under the
 * {@code secret/} KV-v2 mount. Admin reads/writes through
 * {@link com.werkflow.admin.service.VaultCredentialStore}; engine reads directly
 * via its own read-only Vault token.
 *
 * <p>Per ADR-020 (amended) and
 * {@code docs/brainstorm/Brainstorm-Credential-Registry-DB.md} (platform repo).
 */
@Entity
@Table(
    name = "tenant_credentials",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_credentials",
        columnNames = {"tenant_id", "credential_type", "label"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TenantCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    /** Canonical credential type slug (e.g. {@code "smtp"}, {@code "slack-bot-token"}). */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "credentialType must be lowercase alphanumeric with hyphens, starting with a letter")
    @Column(name = "credential_type", nullable = false, length = 128)
    private String credentialType;

    /**
     * Tenant-chosen instance label. Permits multiple credentials of the same type
     * per tenant (e.g. two Slack workspaces). Immutable after creation — changing
     * a label would invalidate Vault references held by deployed BPMN processes.
     */
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "label must be lowercase alphanumeric with hyphens, starting with a letter")
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /**
     * Logical Vault path under the {@code secret/} KV-v2 mount, e.g.
     * {@code tenants/{tenantId}/{credentialType}/{label}}.
     * Persisted at creation; never edited.
     */
    @NotBlank
    @Column(name = "vault_path", nullable = false, length = 500)
    private String vaultPath;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Last rotation timestamp; updated on any value-only PUT to the credential. */
    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    /**
     * Convenience constructor for service-layer creation paths. Audit fields are
     * populated by {@link AuditingEntityListener} on persist/merge.
     */
    public TenantCredential(String tenantId, String credentialType, String label, String vaultPath) {
        this.tenantId = tenantId;
        this.credentialType = credentialType;
        this.label = label;
        this.vaultPath = vaultPath;
    }
}
