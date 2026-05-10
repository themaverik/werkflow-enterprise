package com.werkflow.admin.dto.datasource;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public read model for a registered tenant datasource.
 *
 * <p>The {@code password} field is present in the schema but is always returned
 * as {@code null} for external portal responses — the credential is write-only from
 * the client's perspective (Fix H-5). The decrypted value is only populated in the
 * engine-internal {@code resolveForEngine} call so the engine receives the plaintext
 * credential over the trusted internal channel.</p>
 */
public record TenantDatasourceResponse(
    UUID id,
    String tenantId,
    String ref,
    String jdbcUrl,
    String driverClassName,
    String username,
    /** Null for all external responses (write-only). Decrypted value only in engine-internal resolveForEngine call. */
    String password,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
