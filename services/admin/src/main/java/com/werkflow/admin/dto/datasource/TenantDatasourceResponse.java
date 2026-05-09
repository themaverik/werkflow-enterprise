package com.werkflow.admin.dto.datasource;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public read model for a registered tenant datasource.
 * The {@code passwordSecretRef} is included (it's a key reference, not a password),
 * but the resolved password is never exposed through this API.
 */
public record TenantDatasourceResponse(
    UUID id,
    String tenantId,
    String ref,
    String jdbcUrl,
    String driverClassName,
    String username,
    String passwordSecretRef,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
