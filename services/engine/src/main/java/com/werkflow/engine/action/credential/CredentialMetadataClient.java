package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.dto.CredentialPathDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Looks up the OpenBao path for a {@code (tenantId, credentialType, label)} triple
 * by calling admin-service's internal endpoint.
 *
 * <p>Returns {@link Optional#empty()} on 404 — the caller (typically
 * {@link CredentialRegistry#resolveForTenant}) then falls back to env-based
 * resolution. Any other HTTP failure surfaces as an exception so callers don't
 * silently swallow infrastructure problems.
 *
 * <p>Cached at the {@code credentialPaths} cache (60s TTL configured in
 * {@code CacheConfig}). The cache key includes all three segments so different
 * labels of the same type don't collide.
 */
@Component
@Slf4j
public class CredentialMetadataClient {

    private final RestTemplate restTemplate;
    private final String adminServiceUrl;

    public CredentialMetadataClient(
            RestTemplate restTemplate,
            @Value("${app.admin-service.url:http://localhost:8083}") String adminServiceUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    /**
     * Resolves the Vault path for a credential triple.
     *
     * @return {@code Optional.empty()} if admin returned 404; populated DTO otherwise
     * @throws RuntimeException for any non-404 HTTP failure (timeout, 5xx, etc.)
     */
    @Cacheable(value = "credentialPaths",
        key = "#tenantId + ':' + #credentialType + ':' + #label",
        unless = "#result == null || !#result.isPresent()")
    public Optional<CredentialPathDto> resolvePath(String tenantId, String credentialType, String label) {
        String url = adminServiceUrl
            + "/api/v1/config/credentials/{tenantCode}/{credentialType}/{label}";
        try {
            CredentialPathDto dto = restTemplate.getForObject(
                url, CredentialPathDto.class, tenantId, credentialType, label);
            return Optional.ofNullable(dto);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("No credential metadata for tenant={} type={} label={} — caller may fall back to env",
                    tenantId, credentialType, label);
                return Optional.empty();
            }
            log.error("Credential metadata lookup failed for tenant={} type={} label={}: {}",
                tenantId, credentialType, label, ex.getStatusCode());
            throw ex;
        }
    }
}
