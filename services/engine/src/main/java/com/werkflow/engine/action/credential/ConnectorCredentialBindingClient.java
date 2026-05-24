package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.dto.ConnectorCredentialBindingDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * Looks up the credential a registered connector is bound to (ADR-024 Model A) by calling
 * admin-service's internal {@code /credential-binding} endpoint.
 *
 * <p>Used by {@code RestConnectorDelegate} in connector mode, where the BPMN task references
 * only a {@code connector} and carries no per-task credential fields. Admin owns the
 * {@code authScheme → credential-type} mapping and returns the already-mapped slug + label;
 * the engine resolves that pair against OpenBao via {@link CredentialRegistry}.
 *
 * <p>Returns {@link Optional#empty()} on 204/404 — the connector requires no auth (authScheme
 * NONE), is unregistered, or has no bound credential. Any other HTTP failure surfaces as an
 * exception so infrastructure problems are not silently swallowed.
 *
 * <p>Cached at the {@code connectorCredentialBindings} cache (60s TTL in {@code CacheConfig}),
 * matching the endpoint- and credential-path caches so a rebound connector takes effect within
 * the same window.
 */
@Component
@Slf4j
public class ConnectorCredentialBindingClient {

    private final RestTemplate restTemplate;
    private final String adminServiceUrl;

    public ConnectorCredentialBindingClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.admin-service.url:http://localhost:8083}") String adminServiceUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    /**
     * Resolves the connector's credential binding.
     *
     * @return {@code Optional.empty()} when the connector requires no auth, is unregistered, or
     *         has no bound credential (admin returns 204/404); populated otherwise
     * @throws RuntimeException for any non-404 HTTP failure (timeout, 5xx, etc.)
     */
    // Optional.empty() is deliberately NOT cached: a NONE/unregistered connector must re-resolve
    // each call so that later registering or binding a credential takes effect without waiting for
    // a TTL. The trade-off is one admin round-trip per execution for no-auth connectors — matching
    // the pre-existing CredentialMetadataClient / TenantEndpointResolver pattern. Do not remove the
    // unless clause to "cache empties": that would pin a connector to no-auth after registration.
    @Cacheable(value = "connectorCredentialBindings",
        key = "#tenantCode + ':' + #connectorKey",
        unless = "#result == null || !#result.isPresent()")
    public Optional<ConnectorCredentialBindingDto> resolveBinding(String tenantCode, String connectorKey) {
        String url = UriComponentsBuilder.fromHttpUrl(adminServiceUrl)
            .path("/api/connectors/internal/connectors/{connectorKey}/credential-binding")
            .queryParam("tenantCode", tenantCode)
            .buildAndExpand(connectorKey)
            .toUriString();
        try {
            ConnectorCredentialBindingDto dto =
                restTemplate.getForObject(url, ConnectorCredentialBindingDto.class);
            // 204 No Content deserializes to a null body — the connector needs no auth.
            return Optional.ofNullable(dto);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("No credential binding for tenant={} connector={} — applying no auth",
                    tenantCode, connectorKey);
                return Optional.empty();
            }
            log.error("Credential binding lookup failed for tenant={} connector={}: {}",
                tenantCode, connectorKey, ex.getStatusCode());
            throw ex;
        }
    }
}
