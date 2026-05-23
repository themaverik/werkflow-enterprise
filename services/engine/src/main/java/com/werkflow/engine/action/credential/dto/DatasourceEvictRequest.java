package com.werkflow.engine.action.credential.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/internal/datasources/evict}.
 * Called by admin-service after a datasource update or credential rotation to
 * invalidate the engine's cached HikariCP pool so the next query picks up
 * fresh config/credentials from OpenBao.
 *
 * @param tenantId owning tenant (matches admin entity {@code tenantId})
 * @param ref      datasource reference slug (e.g. {@code "demo-h2-hris"})
 */
public record DatasourceEvictRequest(
    @NotBlank String tenantId,
    @NotBlank String ref
) {}
