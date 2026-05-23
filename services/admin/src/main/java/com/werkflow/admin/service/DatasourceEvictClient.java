package com.werkflow.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls engine's {@code POST /api/internal/datasources/evict} endpoint to invalidate
 * a cached HikariCP pool after a datasource update or credential rotation.
 *
 * <p>This client is best-effort: transport or non-2xx failures are logged as warnings
 * and never rethrown. The admin operation has already committed; a failed eviction
 * means the engine will pick up fresh config on its next cache miss (or after restart).
 *
 * <p>Authenticates as the {@code werkflow-admin} service account via the
 * {@code serviceRestTemplate} bean (Keycloak client_credentials grant).
 *
 * @see com.werkflow.admin.config.RestTemplateConfig#serviceRestTemplate
 */
@Component
@Slf4j
public class DatasourceEvictClient {

    private final RestTemplate restTemplate;
    private final String engineServiceUrl;

    public DatasourceEvictClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.engine-service.url:http://werkflow-engine:8081}") String engineServiceUrl) {
        this.restTemplate = restTemplate;
        this.engineServiceUrl = engineServiceUrl;
    }

    /**
     * Asks the engine to evict the HikariCP pool for the given tenant + datasource ref.
     *
     * <p>Best-effort: any transport failure or non-2xx response is logged as a warning.
     * The caller's transaction has already committed at this point — eviction failure
     * does not roll back the admin update.
     *
     * @param tenantId owning tenant
     * @param ref      datasource reference slug to evict
     */
    public void evict(String tenantId, String ref) {
        String url = engineServiceUrl + "/api/internal/datasources/evict";
        Map<String, String> body = Map.of("tenantId", tenantId, "ref", ref);
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(url, body, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Datasource evict: non-2xx response status={} for tenantId={} ref={}",
                    response.getStatusCode(), tenantId, ref);
            }
        } catch (RestClientException ex) {
            log.warn("Datasource evict: transport failure for tenantId={} ref={} — engine pool may be stale",
                tenantId, ref);
        }
    }
}
