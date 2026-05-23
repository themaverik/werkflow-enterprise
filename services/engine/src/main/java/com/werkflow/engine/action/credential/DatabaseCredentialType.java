package com.werkflow.engine.action.credential;

import com.zaxxer.hikari.HikariConfig;

/**
 * Transport-specific sub-interface of {@link CredentialType} for database credentials
 * (ADR-020 Phase B.4, finalized B.5).
 *
 * <p>Mirrors the {@link HttpCredentialType} decorator pattern: the implementation does not
 * construct or own the connection pool. The caller ({@link com.werkflow.engine.action.db.DatasourceRegistry})
 * builds and owns the {@link HikariConfig} — including pool name, autoCommit, sizing, and
 * timeouts — and the credential type only injects the resolved username and password.
 * This keeps tenant-specific pool policy in the registry (the only layer with tenant context).
 *
 * <p>Implementations are Spring beans auto-registered by {@link CredentialRegistry}. Because
 * this interface extends {@link CredentialType}, every database credential also participates
 * in the storage and UI surface (name, displayName, fields, validate).
 *
 * @see CredentialType
 * @see HttpCredentialType
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
public interface DatabaseCredentialType extends CredentialType {

    /**
     * Injects the resolved credential values (username + password) into the supplied
     * {@link HikariConfig}. MUST NOT make network calls or build the pool — the caller
     * constructs the {@link com.zaxxer.hikari.HikariDataSource} after this returns.
     *
     * @param config the HikariConfig to mutate; never {@code null}
     * @param values the resolved credential values; never {@code null}
     */
    void applyCredentials(HikariConfig config, CredentialValues values);
}
