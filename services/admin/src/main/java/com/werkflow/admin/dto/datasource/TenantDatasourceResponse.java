package com.werkflow.admin.dto.datasource;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public read model for a registered tenant datasource. Carries no credential values —
 * the username/password live in OpenBao behind {@code credentialRef}.
 */
public record TenantDatasourceResponse(
    UUID id,
    String tenantId,
    String ref,
    String jdbcUrl,
    String driverClassName,
    String credentialRef,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
