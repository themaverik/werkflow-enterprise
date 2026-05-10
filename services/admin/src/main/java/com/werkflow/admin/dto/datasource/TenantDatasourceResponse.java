package com.werkflow.admin.dto.datasource;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public read model for a registered tenant datasource.
 *
 * <p>The {@code passwordSecretRef} field is present in the schema but is always
 * returned as {@code null} — the secret key reference is write-only from the
 * client's perspective. Clients that need to rotate the ref supply a new value
 * on the next PUT; they never need to read back what was stored (Fix H-5).</p>
 */
public record TenantDatasourceResponse(
    UUID id,
    String tenantId,
    String ref,
    String jdbcUrl,
    String driverClassName,
    String username,
    /** Always null in responses — the secret ref is write-only. */
    String passwordSecretRef,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
