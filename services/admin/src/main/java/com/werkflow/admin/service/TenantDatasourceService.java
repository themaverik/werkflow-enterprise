package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CredentialPathResponse;
import com.werkflow.admin.dto.datasource.DatasourceEngineConfig;
import com.werkflow.admin.dto.datasource.DatasourceTestResult;
import com.werkflow.admin.dto.datasource.TenantDatasourceRequest;
import com.werkflow.admin.dto.datasource.TenantDatasourceResponse;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.event.DatasourcePoolEvictionEvent;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD operations and live connection testing for tenant datasource registrations.
 *
 * <p>All read/write operations are tenant-scoped — callers pass their {@code tenantId}
 * from the JWT, and the service enforces isolation before returning results.</p>
 *
 * <p>Credentials are resolved from OpenBao via {@link VaultCredentialStore} at connection-test
 * time. The plaintext credential is never stored in the database — only a {@code credentialRef}
 * is persisted, which names a {@code jdbc-password} credential managed via the credentials API.</p>
 *
 * <p>SSRF protection is applied on {@link #create} and {@link #update}: the JDBC URL
 * scheme must be in the allowlist and the host must not resolve to a private/loopback
 * address (production only — see {@code app.environment}). The driver class name must
 * also be in the server-side allowlist.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDatasourceService {

    private static final String JDBC_CREDENTIAL_TYPE = "jdbc-password";

    private final TenantDatasourceRepository repository;
    private final TenantCredentialService credentialService;
    private final VaultCredentialStore vault;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.environment:development}")
    private String appEnvironment;

    // -------------------------------------------------------------------------
    // Security allowlists (C-1, C-2)
    // -------------------------------------------------------------------------

    /** Permitted JDBC URL prefixes — prevents SSRF via exotic JDBC schemes. */
    private static final Set<String> ALLOWED_JDBC_SCHEMES = Set.of(
        "jdbc:postgresql", "jdbc:mysql", "jdbc:mariadb",
        "jdbc:oracle:thin", "jdbc:sqlserver", "jdbc:h2:tcp", "jdbc:h2:mem"
    );

    /** Extracts the host portion from standard JDBC URLs. */
    private static final Pattern JDBC_HOST_PATTERN =
        Pattern.compile("(?i)jdbc:[^:]+://([^/:;?@]+)");

    /** Permitted JDBC driver class names — prevents arbitrary class loading. */
    private static final Set<String> ALLOWED_DRIVER_CLASSES = Set.of(
        "org.postgresql.Driver",
        "com.mysql.cj.jdbc.Driver",
        "oracle.jdbc.OracleDriver",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "org.h2.Driver"
    );

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
     */
    @Transactional(readOnly = true)
    public TenantDatasource getEntity(String tenantId, String ref) {
        return resolve(tenantId, ref);
    }

    /**
     * Registers a new datasource for the tenant.
     * The {@code credentialRef} in the request names an existing {@code jdbc-password}
     * credential — no credential values are persisted here.
     *
     * @throws ResponseStatusException 409 if the (tenantId, ref) pair already exists
     * @throws ResponseStatusException 400 if the JDBC URL or driver class is not permitted
     */
    @Transactional
    public TenantDatasourceResponse create(String tenantId, TenantDatasourceRequest request) {
        validateJdbcUrl(request.jdbcUrl());
        if (!ALLOWED_DRIVER_CLASSES.contains(request.driverClassName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Driver class not permitted: " + request.driverClassName());
        }
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
     * The {@code credentialRef} may be updated to point at a different credential.
     *
     * @throws ResponseStatusException 404 if not found
     * @throws ResponseStatusException 400 if the JDBC URL or driver class is not permitted
     */
    @Transactional
    public TenantDatasourceResponse update(String tenantId, String ref, TenantDatasourceRequest request) {
        validateJdbcUrl(request.jdbcUrl());
        if (!ALLOWED_DRIVER_CLASSES.contains(request.driverClassName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Driver class not permitted: " + request.driverClassName());
        }
        TenantDatasource entity = resolve(tenantId, ref);
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setDriverClassName(request.driverClassName());
        entity.setCredentialRef(request.credentialRef());
        entity.setDialect(request.dialect());
        entity.setPoolMinSize(request.poolMinSize() > 0 ? request.poolMinSize() : 1);
        entity.setPoolMaxSize(request.poolMaxSize() > 0 ? request.poolMaxSize() : 5);
        entity.setConnectionTimeoutSeconds(request.connectionTimeoutSeconds() > 0 ? request.connectionTimeoutSeconds() : 5);
        entity.setIdleTimeoutSeconds(request.idleTimeoutSeconds() > 0 ? request.idleTimeoutSeconds() : 600);
        TenantDatasource saved = repository.save(entity);
        log.info("tenant.datasource.updated tenantId={} ref={}", tenantId, ref);
        // Evict the engine's cached pool after commit so the next query picks up
        // updated config/credentials (B.5 D7). AFTER_COMMIT-scoped listener.
        eventPublisher.publishEvent(new DatasourcePoolEvictionEvent(tenantId, ref));
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
     * <p>The credential is resolved tenant-scoped from OpenBao via
     * {@link TenantCredentialService} and {@link VaultCredentialStore} (IDOR-safe).
     * JDBC exceptions are categorized before returning to avoid leaking internal
     * topology or credential details to the client (M-1).</p>
     *
     * @return test result with ok=true and latency, or ok=false with a safe error message
     */
    public DatasourceTestResult testConnection(String tenantId, String ref) {
        TenantDatasource entity = resolve(tenantId, ref);

        String username;
        String password;
        try {
            CredentialPathResponse path = credentialService.resolvePath(
                tenantId, JDBC_CREDENTIAL_TYPE, entity.getCredentialRef());
            Map<String, Object> values = vault.read(path.vaultPath())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "empty"));
            Object u = values.get("username");
            Object p = values.get("password");
            if (u == null || u.toString().isBlank() || p == null || p.toString().isBlank()) {
                return new DatasourceTestResult(false,
                    "Credential '" + entity.getCredentialRef() + "' is missing username or password", 0);
            }
            username = u.toString();
            password = p.toString();
        } catch (ResponseStatusException e) {
            return new DatasourceTestResult(false,
                "Credential '" + entity.getCredentialRef() + "' not found or empty", 0);
        } catch (RuntimeException e) {
            // e.g. VaultException when OpenBao is unreachable — fail gracefully, never 500.
            log.warn("tenant.datasource.test.credential-resolve-failed tenantId={} ref={} error={}",
                tenantId, ref, e.getMessage());
            return new DatasourceTestResult(false, "Credential resolution failed", 0);
        }

        long start = System.currentTimeMillis();
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName(entity.getDriverClassName());
            ds.setUrl(entity.getJdbcUrl());
            ds.setUsername(username);
            ds.setPassword(password);

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
            return new DatasourceTestResult(false, categorizeJdbcError(e), latency);
        }
    }

    // -------------------------------------------------------------------------
    // Internal engine endpoint — returns only non-secret config + credentialRef
    // -------------------------------------------------------------------------

    /**
     * Returns only non-secret config plus credentialRef; the engine resolves
     * the credential from OpenBao.
     *
     * <p>This is an internal endpoint; it is not exposed in the public API.</p>
     */
    @Transactional(readOnly = true)
    public DatasourceEngineConfig resolveForEngine(String tenantId, String ref) {
        TenantDatasource e = resolve(tenantId, ref);
        return new DatasourceEngineConfig(
            e.getJdbcUrl(), e.getDriverClassName(), e.getCredentialRef(), e.getDialect(),
            e.getPoolMinSize(), e.getPoolMaxSize(),
            e.getConnectionTimeoutSeconds(), e.getIdleTimeoutSeconds());
    }

    // -------------------------------------------------------------------------
    // Security helpers (C-1, M-1)
    // -------------------------------------------------------------------------

    /**
     * Validates that the JDBC URL uses a permitted scheme and, in production only,
     * does not resolve to a private or loopback address (SSRF protection).
     *
     * <p>The scheme allowlist check always runs regardless of environment.
     * The private-address check is gated on {@code app.environment=production}
     * because dev and Docker environments legitimately connect to Docker-internal
     * or loopback addresses.</p>
     *
     * @throws ResponseStatusException 400 if the URL is not permitted
     */
    private void validateJdbcUrl(String url) {
        if (url == null || ALLOWED_JDBC_SCHEMES.stream().noneMatch(url.toLowerCase()::startsWith)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "JDBC URL scheme not permitted. Allowed prefixes: " + ALLOWED_JDBC_SCHEMES);
        }
        // Private-address SSRF check applies only in production.
        // Dev and test environments connect to Docker-internal or loopback databases.
        if (!"production".equalsIgnoreCase(appEnvironment)) {
            return;
        }
        Matcher m = JDBC_HOST_PATTERN.matcher(url);
        if (m.find()) {
            String host = m.group(1);
            try {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() ||
                    addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "JDBC URL resolves to a private or loopback address");
                }
            } catch (UnknownHostException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "JDBC URL host cannot be resolved");
            }
        }
    }

    /**
     * Translates raw JDBC exception messages into safe, categorized messages
     * that do not leak internal topology or credential details.
     *
     * @param e the exception caught during connection test
     * @return a safe message suitable for returning to the client
     */
    private String categorizeJdbcError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("connection refused") || msg.contains("timed out") || msg.contains("timeout")) {
            return "Host unreachable or port closed";
        }
        if (msg.contains("authentication") || msg.contains("password") || msg.contains("credential")) {
            return "Authentication failed — check username and secret ref";
        }
        if (msg.contains("unknown host") || msg.contains("nodename nor servname")
                || msg.contains("name or service not known")) {
            return "Host not found — check JDBC URL";
        }
        if (msg.contains("database") && msg.contains("not exist")) {
            return "Database not found — check JDBC URL";
        }
        return "Connection failed — check JDBC URL and credentials";
    }

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
            e.getDriverClassName(), e.getCredentialRef(),
            e.getDialect(), e.getPoolMinSize(), e.getPoolMaxSize(),
            e.getConnectionTimeoutSeconds(), e.getIdleTimeoutSeconds(),
            e.getCreatedAt(), e.getUpdatedAt());
    }

    private TenantDatasource fromRequest(String tenantId, TenantDatasourceRequest r) {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId(tenantId);
        e.setRef(r.ref());
        e.setJdbcUrl(r.jdbcUrl());
        e.setDriverClassName(r.driverClassName());
        e.setCredentialRef(r.credentialRef());
        e.setDialect(r.dialect());
        e.setPoolMinSize(r.poolMinSize() > 0 ? r.poolMinSize() : 1);
        e.setPoolMaxSize(r.poolMaxSize() > 0 ? r.poolMaxSize() : 5);
        e.setConnectionTimeoutSeconds(r.connectionTimeoutSeconds() > 0 ? r.connectionTimeoutSeconds() : 5);
        e.setIdleTimeoutSeconds(r.idleTimeoutSeconds() > 0 ? r.idleTimeoutSeconds() : 600);
        return e;
    }
}
