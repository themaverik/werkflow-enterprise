package com.werkflow.admin.dto.datasource;

/**
 * Engine-internal projection returned by {@code resolveForEngine}. Carries only non-secret
 * config plus the {@code credentialRef}; the engine resolves the credential from OpenBao.
 * Field names mirror the engine's {@code DatasourceConfig} record for direct deserialization.
 */
public record DatasourceEngineConfig(
    String jdbcUrl,
    String driverClassName,
    String credentialRef,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds
) {}
