package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.db.DatasourceRegistry;

import javax.sql.DataSource;

/**
 * Transport-specific sub-interface of {@link CredentialType} for database credentials
 * (ADR-020 Phase B.4).
 *
 * <p>This interface was deferred from {@link CredentialType}'s original design because a
 * uniform {@code applyTo(Object)} signature was rejected in favour of transport-specific
 * sub-interfaces — database pool construction requires both credential values
 * (username + password) and non-credential pool/JDBC config (URL, driver, pool sizing,
 * timeouts), making a two-parameter signature the natural fit.
 *
 * <p>The second parameter uses {@link DatasourceRegistry.DatasourceConfigDto} — the same
 * DTO the registry already carries from the admin service — so implementations can build
 * a {@link DataSource} without needing a separate config abstraction. Credential values
 * overlay the {@code username} and {@code password} fields from {@code CredentialValues}
 * instead of the DTO's placeholders, allowing the registry to keep injecting JDBC URL,
 * driver class, and pool parameters independently.
 *
 * <p>Implementations are Spring beans auto-registered by {@link CredentialRegistry}.
 * Because this interface extends {@link CredentialType}, every database credential also
 * participates in the storage and UI surface (name, displayName, fields, validate).
 *
 * @see CredentialType
 * @see HttpCredentialType
 * @see DatasourceRegistry
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
public interface DatabaseCredentialType extends CredentialType {

    /**
     * Builds a configured {@link DataSource} from the supplied credential values and
     * non-credential pool/JDBC configuration.
     *
     * <p>Implementations are responsible for constructing an appropriate connection pool
     * (e.g. HikariCP) using:
     * <ul>
     *   <li>{@code values} — supplies the runtime username and password (decrypted by
     *       the admin service before reaching this layer)</li>
     *   <li>{@code config} — supplies all non-secret config: JDBC URL, driver class name,
     *       pool sizing, and timeout settings</li>
     * </ul>
     *
     * <p>Callers (i.e. {@link DatasourceRegistry}) are responsible for caching the
     * returned {@link DataSource} — this method may be called on every cache miss and
     * MUST NOT assume a singleton lifecycle.
     *
     * @param values the resolved credential values (username + password); never {@code null}
     * @param config the non-credential JDBC and pool configuration; never {@code null}
     * @return a fully configured, ready-to-use {@link DataSource}
     */
    DataSource buildDataSource(CredentialValues values, DatasourceRegistry.DatasourceConfigDto config);
}
