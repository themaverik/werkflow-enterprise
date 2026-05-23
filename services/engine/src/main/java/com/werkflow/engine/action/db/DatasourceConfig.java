package com.werkflow.engine.action.db;

/**
 * Non-secret datasource configuration fetched from the admin service (ADR-020 Phase B.5).
 *
 * <p>Replaces the former {@code DatasourceRegistry.DatasourceConfigDto}. Carries no
 * credential values — the username and password are resolved from OpenBao by
 * {@link com.werkflow.engine.action.credential.CredentialRegistry} keyed on
 * {@code credentialRef}. The admin service no longer ships plaintext credentials.
 */
public record DatasourceConfig(
    String jdbcUrl,
    String driverClassName,
    String credentialRef,
    String dialect,
    int poolMinSize,
    int poolMaxSize,
    int connectionTimeoutSeconds,
    int idleTimeoutSeconds
) {}
