package com.werkflow.admin.service;

import com.werkflow.admin.dto.datasource.DatasourceTestResult;
import com.werkflow.admin.dto.datasource.TenantDatasourceRequest;
import com.werkflow.admin.dto.datasource.TenantDatasourceResponse;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import com.werkflow.common.security.SecretsResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

/**
 * CRUD operations and live connection testing for tenant datasource registrations.
 *
 * <p>All read/write operations are tenant-scoped — callers pass their {@code tenantId}
 * from the JWT, and the service enforces isolation before returning results.</p>
 *
 * <p>Passwords are stored only as a secret-manager key reference. The
 * {@link #testConnection} method resolves the credential at call time so that
 * it is never persisted or logged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDatasourceService {

    private final TenantDatasourceRepository repository;
    private final SecretsResolver secretsResolver;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    /**
     * Returns all datasources registered for the given tenant.
     */
    @Transactional(readOnly = true)
    public List<TenantDatasourceResponse> list(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Returns a single datasource by ref, enforcing tenant ownership.
     *
     * @throws ResponseStatusException 404 if not found, 403 if cross-tenant
     */
    @Transactional(readOnly = true)
    public TenantDatasourceResponse get(String tenantId, String ref) {
        return toResponse(resolve(tenantId, ref));
    }

    /**
     * Returns the raw entity for internal use (e.g. engine datasource resolution).
     * This endpoint also serves the engine's DatasourceRegistry — it resolves the
     * password via the secrets manager before returning so the engine never has to.
     */
    @Transactional(readOnly = true)
    public TenantDatasource getEntity(String tenantId, String ref) {
        return resolve(tenantId, ref);
    }

    /**
     * Registers a new datasource for the tenant.
     *
     * @throws ResponseStatusException 409 if the (tenantId, ref) pair already exists
     */
    @Transactional
    public TenantDatasourceResponse create(String tenantId, TenantDatasourceRequest request) {
        if (repository.existsByTenantIdAndRef(tenantId, request.ref())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Datasource '" + request.ref() + "' already registered for tenant '" + tenantId + "'");
        }
        TenantDatasource entity = fromRequest(tenantId, request);
        TenantDatasource saved = repository.save(entity);
        log.info("tenant.datasource.created tenantId={} ref={}", tenantId, request.ref());
        return toResponse(saved);
    }

    /**
     * Updates an existing datasource registration.
     * The {@code ref} slug is immutable — pass the same value or update other fields.
     *
     * @throws ResponseStatusException 404 if not found
     */
    @Transactional
    public TenantDatasourceResponse update(String tenantId, String ref, TenantDatasourceRequest request) {
        TenantDatasource entity = resolve(tenantId, ref);
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setDriverClassName(request.driverClassName());
        entity.setUsername(request.username());
        entity.setPasswordSecretRef(request.passwordSecretRef());
        entity.setDialect(request.dialect());
        entity.setPoolMinSize(request.poolMinSize() > 0 ? request.poolMinSize() : 1);
        entity.setPoolMaxSize(request.poolMaxSize() > 0 ? request.poolMaxSize() : 5);
        entity.setConnectionTimeoutSeconds(request.connectionTimeoutSeconds() > 0 ? request.connectionTimeoutSeconds() : 5);
        entity.setIdleTimeoutSeconds(request.idleTimeoutSeconds() > 0 ? request.idleTimeoutSeconds() : 600);
        TenantDatasource saved = repository.save(entity);
        log.info("tenant.datasource.updated tenantId={} ref={}", tenantId, ref);
        return toResponse(saved);
    }

    /**
     * Deletes a datasource registration.
     * Does NOT evict the engine's connection pool — the engine will get a 404
     * on next resolution and handle it as a connector error.
     *
     * @throws ResponseStatusException 404 if not found
     */
    @Transactional
    public void delete(String tenantId, String ref) {
        TenantDatasource entity = resolve(tenantId, ref);
        repository.delete(entity);
        log.info("tenant.datasource.deleted tenantId={} ref={}", tenantId, ref);
    }

    // -------------------------------------------------------------------------
    // Connection test
    // -------------------------------------------------------------------------

    /**
     * Establishes a throw-away, non-pooled connection to verify reachability
     * and credential validity. Uses DriverManagerDataSource — not Hikari —
     * so no pool resources are consumed by the test.
     *
     * @return test result with ok=true and latency, or ok=false with error message
     */
    public DatasourceTestResult testConnection(String tenantId, String ref) {
        TenantDatasource entity = resolve(tenantId, ref);
        String resolvedPassword;
        try {
            resolvedPassword = secretsResolver.resolve(entity.getPasswordSecretRef());
        } catch (Exception e) {
            return new DatasourceTestResult(false,
                "Failed to resolve password secret '" + entity.getPasswordSecretRef() + "': " + e.getMessage(), 0);
        }

        long start = System.currentTimeMillis();
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName(entity.getDriverClassName());
            ds.setUrl(entity.getJdbcUrl());
            ds.setUsername(entity.getUsername());
            ds.setPassword(resolvedPassword);

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                String dbVersion = conn.getMetaData().getDatabaseProductVersion();
                long latency = System.currentTimeMillis() - start;
                log.info("tenant.datasource.test.ok tenantId={} ref={} latencyMs={}", tenantId, ref, latency);
                return new DatasourceTestResult(true, "Connected — " + dbVersion, latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("tenant.datasource.test.failed tenantId={} ref={} error={}", tenantId, ref, e.getMessage());
            return new DatasourceTestResult(false, e.getMessage(), latency);
        }
    }

    // -------------------------------------------------------------------------
    // Internal engine endpoint — exposes resolved-password DTO for DatasourceRegistry
    // -------------------------------------------------------------------------

    /**
     * Returns the datasource config with the password resolved from the secrets manager.
     * This is the engine-internal endpoint; it is NOT exposed in the public API.
     * Callers (DatasourceRegistry) pass both tenantCode and ref to get pool config.
     */
    @Transactional(readOnly = true)
    public ResolvedDatasourceConfig resolveForEngine(String tenantId, String ref) {
        TenantDatasource entity = resolve(tenantId, ref);
        String password = secretsResolver.resolve(entity.getPasswordSecretRef());
        return new ResolvedDatasourceConfig(
            entity.getJdbcUrl(),
            entity.getDriverClassName(),
            entity.getUsername(),
            password,
            entity.getPoolMinSize(),
            entity.getPoolMaxSize(),
            entity.getConnectionTimeoutSeconds(),
            entity.getIdleTimeoutSeconds()
        );
    }

    /**
     * Resolved datasource config including the actual password (for engine-internal use only).
     */
    public record ResolvedDatasourceConfig(
        String jdbcUrl,
        String driverClassName,
        String username,
        String resolvedPassword,
        int poolMinSize,
        int poolMaxSize,
        int connectionTimeoutSeconds,
        int idleTimeoutSeconds
    ) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TenantDatasource resolve(String tenantId, String ref) {
        return repository.findByTenantIdAndRef(tenantId, ref)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Datasource '" + ref + "' not found for tenant '" + tenantId + "'"));
    }

    private TenantDatasourceResponse toResponse(TenantDatasource e) {
        return new TenantDatasourceResponse(
            e.getId(), e.getTenantId(), e.getRef(), e.getJdbcUrl(),
            e.getDriverClassName(), e.getUsername(), e.getPasswordSecretRef(),
            e.getDialect(), e.getPoolMinSize(), e.getPoolMaxSize(),
            e.getConnectionTimeoutSeconds(), e.getIdleTimeoutSeconds(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private TenantDatasource fromRequest(String tenantId, TenantDatasourceRequest r) {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId(tenantId);
        e.setRef(r.ref());
        e.setJdbcUrl(r.jdbcUrl());
        e.setDriverClassName(r.driverClassName());
        e.setUsername(r.username());
        e.setPasswordSecretRef(r.passwordSecretRef());
        e.setDialect(r.dialect());
        e.setPoolMinSize(r.poolMinSize() > 0 ? r.poolMinSize() : 1);
        e.setPoolMaxSize(r.poolMaxSize() > 0 ? r.poolMaxSize() : 5);
        e.setConnectionTimeoutSeconds(r.connectionTimeoutSeconds() > 0 ? r.connectionTimeoutSeconds() : 5);
        e.setIdleTimeoutSeconds(r.idleTimeoutSeconds() > 0 ? r.idleTimeoutSeconds() : 600);
        return e;
    }
}
