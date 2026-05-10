package com.werkflow.engine.action.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-tenant DataSource instances for the database connector transport.
 *
 * <p>Each {@code (tenantCode, datasourceRef)} pair gets its own HikariCP pool, loaded
 * once from the admin service and cached in memory. This means one slow tenant DB cannot
 * starve another tenant's queries. The pool config comes from the connector's transport
 * config (pool.minSize, maxSize, connectionTimeoutSeconds, idleTimeoutSeconds).</p>
 *
 * <p>Each connector key also gets a Resilience4j circuit breaker keyed by
 * {@code {tenantCode}:{connectorKey}}, with a 5-failure threshold and 30-second reset
 * window by default. The circuit breaker prevents workflow threads piling up against
 * a dead database — it opens after repeated failures and half-opens to probe recovery.</p>
 *
 * <p>The admin service's internal {@code resolveForEngine} endpoint returns the
 * decrypted password directly over the trusted service-to-service channel.
 * The engine uses it directly — no local {@code SecretsResolver} is required.</p>
 *
 * <p>Fix H-2: the pool cache is keyed by a typed {@link DsKey} record rather than
 * string concatenation to eliminate potential key collisions between tenant codes
 * and datasource refs that contain colons.</p>
 *
 * <p>Datasource definitions are fetched from the admin service at first use and cached.
 * Call {@link #evict(String, String)} to force a refresh after a datasource update.</p>
 */
@Slf4j
@Service
public class DatasourceRegistry {

    private static final int CB_FAILURE_THRESHOLD = 5;
    private static final int CB_RESET_TIMEOUT_SECONDS = 30;

    private final RestTemplate serviceRestTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final String adminServiceUrl;

    /**
     * H-2: typed cache key — eliminates string-concatenation collision between
     * tenantCode and datasourceRef values that contain colons.
     */
    private record DsKey(String tenantCode, String ref) {}

    /** Cache of live HikariDataSource instances keyed by typed (tenantCode, ref) pair. */
    private final ConcurrentHashMap<DsKey, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    /** Cache of raw datasource config DTOs keyed by typed (tenantCode, ref) pair. */
    private final ConcurrentHashMap<DsKey, DatasourceConfigDto> configCache = new ConcurrentHashMap<>();

    public DatasourceRegistry(
            @org.springframework.beans.factory.annotation.Qualifier("serviceRestTemplate")
            RestTemplate serviceRestTemplate,
            @Value("${app.admin-service.url:http://localhost:8083}") String adminServiceUrl) {
        this.serviceRestTemplate = serviceRestTemplate;
        this.adminServiceUrl = adminServiceUrl;

        // Default circuit breaker registry with our platform-standard config.
        // Connectors with custom thresholds could override per-key, but the spec
        // only defines global defaults, so we use a shared config here.
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(CB_FAILURE_THRESHOLD * 10.0f) // 50% of 10-call window
            .slidingWindowSize(CB_FAILURE_THRESHOLD * 2)
            .waitDurationInOpenState(Duration.ofSeconds(CB_RESET_TIMEOUT_SECONDS))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link DataSource} for the given tenant + datasource reference.
     *
     * <p>On the first call the pool is created from config fetched from the admin service.
     * Subsequent calls return the cached instance directly.</p>
     *
     * @param tenantCode    the tenant identifier
     * @param datasourceRef the datasource reference slug (e.g. "demo-h2-hris")
     * @return a live HikariCP DataSource
     * @throws RuntimeException if the datasource config cannot be fetched or the pool cannot be created
     */
    public DataSource resolve(String tenantCode, String datasourceRef) {
        DsKey key = new DsKey(tenantCode, datasourceRef);
        return poolCache.computeIfAbsent(key, k -> createPool(tenantCode, datasourceRef));
    }

    /**
     * Returns the circuit breaker for a specific connector invocation.
     * Circuit breakers are keyed by {@code {tenantCode}:{connectorKey}} so that
     * a circuit that opens for one connector does not block other connectors
     * using the same datasource.
     *
     * @param tenantCode   tenant identifier
     * @param connectorKey BPMN connector key
     */
    public CircuitBreaker circuitBreaker(String tenantCode, String connectorKey) {
        return circuitBreakerRegistry.circuitBreaker(tenantCode + ":" + connectorKey);
    }

    /**
     * Evicts and closes the cached DataSource for the given tenant + ref.
     * The next call to {@link #resolve} will re-fetch the config and create a new pool.
     * Call this after a datasource update or credential rotation.
     *
     * <p>Fix L-2: also evicts all circuit breakers for this tenant so that a
     * re-created datasource starts with a fresh circuit state.</p>
     *
     * @param tenantCode    tenant identifier
     * @param datasourceRef datasource reference slug
     */
    public void evict(String tenantCode, String datasourceRef) {
        DsKey key = new DsKey(tenantCode, datasourceRef);
        configCache.remove(key);
        HikariDataSource ds = poolCache.remove(key);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("DatasourceRegistry: evicted pool for tenantCode={} ref={}", tenantCode, datasourceRef);
        }
        // L-2: evict all circuit breakers for this tenant (any connectorKey)
        circuitBreakerRegistry.getAllCircuitBreakers().stream()
            .filter(cb -> cb.getName().startsWith(tenantCode + ":"))
            .map(CircuitBreaker::getName)
            .toList()
            .forEach(circuitBreakerRegistry::remove);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private HikariDataSource createPool(String tenantCode, String datasourceRef) {
        DatasourceConfigDto config = fetchConfig(tenantCode, datasourceRef);

        // Admin service decrypts the password before returning it over the
        // trusted internal channel — use it directly here.
        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(config.jdbcUrl());
        hk.setDriverClassName(config.driverClassName());
        hk.setUsername(config.username());
        hk.setPassword(config.password());
        hk.setMinimumIdle(config.poolMinSize());
        hk.setMaximumPoolSize(config.poolMaxSize());
        hk.setConnectionTimeout(Duration.ofSeconds(config.connectionTimeoutSeconds()).toMillis());
        hk.setIdleTimeout(Duration.ofSeconds(config.idleTimeoutSeconds()).toMillis());
        hk.setPoolName("werkflow-db-" + tenantCode + "-" + datasourceRef);
        hk.setAutoCommit(true);

        log.info("DatasourceRegistry: creating pool tenant={} ref={} maxSize={}",
            tenantCode, datasourceRef, config.poolMaxSize());
        return new HikariDataSource(hk);
    }

    private DatasourceConfigDto fetchConfig(String tenantCode, String datasourceRef) {
        DsKey key = new DsKey(tenantCode, datasourceRef);
        return configCache.computeIfAbsent(key, k -> {
            String url = adminServiceUrl + "/api/v1/config/datasources/{tenantCode}/{ref}";
            log.debug("DatasourceRegistry: fetching config for tenantCode={} ref={}", tenantCode, datasourceRef);
            return serviceRestTemplate.getForObject(url, DatasourceConfigDto.class, tenantCode, datasourceRef);
        });
    }

    // -------------------------------------------------------------------------
    // DTO for the admin service response
    // -------------------------------------------------------------------------

    /**
     * Projection of the admin service's TenantDatasourceResponse.
     *
     * <p>The admin service's internal {@code resolveForEngine} endpoint decrypts the
     * password before returning, so this DTO carries the plaintext {@code password}
     * directly. No local {@code SecretsResolver} is required.</p>
     */
    public record DatasourceConfigDto(
        String jdbcUrl,
        String driverClassName,
        String username,
        String password,
        int poolMinSize,
        int poolMaxSize,
        int connectionTimeoutSeconds,
        int idleTimeoutSeconds
    ) {}
}
