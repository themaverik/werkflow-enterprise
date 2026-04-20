package com.werkflow.engine.action;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a logical connector key to a base URL for a given tenant.
 *
 * Resolution chain:
 *   1. Caffeine cache (TTL 60s, key = tenantCode:connectorKey:environment)
 *   2. Admin service REST: GET /internal/endpoints/resolve
 *   3. Global fallback from app.services.fallback.* config
 *   4. Throw IllegalStateException if none found
 */
@Slf4j
@Component
public class TenantEndpointResolver {

    private final RestTemplate adminTemplate;
    private final String adminServiceUrl;
    private final String environment;
    private final Map<String, String> globalFallbacks;
    private final Cache<String, String> cache;

    public TenantEndpointResolver(
            @Qualifier("serviceRestTemplate") RestTemplate adminTemplate,
            @Value("${app.admin-service.url}") String adminServiceUrl,
            @Value("${app.environment:development}") String environment,
            @Value("#{${app.services.fallback:{:}}}") Map<String, String> globalFallbacks,
            @Value("${app.endpoint-resolver.cache.ttl-seconds:60}") long cacheTtlSeconds,
            @Value("${app.endpoint-resolver.cache.max-size:500}") long cacheMaxSize) {
        this.adminTemplate = adminTemplate;
        this.adminServiceUrl = adminServiceUrl;
        this.environment = environment;
        this.globalFallbacks = Map.copyOf(globalFallbacks);
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(cacheMaxSize)
            .build();
    }

    /**
     * Resolves the base URL for a connector key scoped to a tenant.
     * Results are cached for the configured TTL duration.
     */
    public String resolve(String tenantCode, String connectorKey) {
        Objects.requireNonNull(tenantCode, "tenantCode must not be null");
        Objects.requireNonNull(connectorKey, "connectorKey must not be null");
        return cache.get(buildCacheKey(tenantCode, connectorKey), k -> loadUrl(tenantCode, connectorKey));
    }

    /**
     * Evicts the cached URL for the given tenant/connector pair.
     * Call this after an admin update to the connector endpoint record.
     */
    public void invalidate(String tenantCode, String connectorKey) {
        Objects.requireNonNull(tenantCode, "tenantCode must not be null");
        Objects.requireNonNull(connectorKey, "connectorKey must not be null");
        cache.invalidate(buildCacheKey(tenantCode, connectorKey));
        log.info("Evicted endpoint cache for {}/{}", tenantCode, connectorKey);
    }

    private String loadUrl(String tenantCode, String connectorKey) {
        try {
            String resolveUrl = UriComponentsBuilder.fromHttpUrl(adminServiceUrl)
                .path("/internal/endpoints/resolve")
                .queryParam("tenantCode", tenantCode)
                .queryParam("connectorKey", connectorKey)
                .queryParam("environment", environment)
                .toUriString();
            String url = adminTemplate.getForObject(resolveUrl, String.class);
            if (url != null && !url.isBlank()) {
                log.debug("Resolved {}/{} -> {}", tenantCode, connectorKey, url);
                return url;
            }
        } catch (RestClientException e) {
            log.warn("Admin unreachable for {}/{}: {}", tenantCode, connectorKey, e.getMessage());
        }

        String fallback = globalFallbacks.get(connectorKey);
        if (fallback != null) {
            log.debug("Using global fallback for {}: {}", connectorKey, fallback);
            return fallback;
        }

        throw new IllegalStateException(
            "No endpoint configured for connector '" + connectorKey
            + "' (tenant: " + tenantCode + ", env: " + environment + ")");
    }

    private String buildCacheKey(String tenantCode, String connectorKey) {
        return tenantCode + ":" + connectorKey + ":" + environment;
    }
}
