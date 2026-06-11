package com.werkflow.admin.service;

import com.werkflow.admin.dto.EngineSeedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Calls engine's {@code POST /api/internal/examples/seed/{tenantId}} endpoint to seed
 * example workflows for a tenant.
 *
 * <p>This client is best-effort: transport or non-2xx failures are logged as warnings
 * and never rethrown. Seeding can always be re-triggered via the portal endpoint.
 *
 * <p>Authenticates as the {@code werkflow-admin} service account via the
 * {@code serviceRestTemplate} bean (Keycloak client_credentials grant).
 *
 * @see com.werkflow.admin.config.RestTemplateConfig#serviceRestTemplate
 */
@Component
@Slf4j
public class ExampleSeedClient {

    private final RestTemplate restTemplate;
    private final String engineServiceUrl;

    public ExampleSeedClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.engine-service.url:http://werkflow-engine:8081}") String engineServiceUrl) {
        this.restTemplate = restTemplate;
        this.engineServiceUrl = engineServiceUrl;
    }

    /**
     * Asks the engine to seed example workflows for the given tenant.
     *
     * <p>Best-effort: any transport failure or non-2xx response is logged as a warning
     * and returns {@link Optional#empty()}. The caller's operation has already committed
     * at this point — seeding failure does not roll back tenant provisioning.
     *
     * @param tenantId the tenant code to seed examples for
     * @return the seed result, or empty on failure
     */
    public Optional<EngineSeedResult> seed(String tenantId) {
        String url = engineServiceUrl + "/api/internal/examples/seed/" + tenantId;
        try {
            ResponseEntity<EngineSeedResult> response = restTemplate.postForEntity(url, null, EngineSeedResult.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Optional.ofNullable(response.getBody());
            }
            log.warn("Example seed: non-2xx response status={} for tenantId={}",
                response.getStatusCode(), tenantId);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Example seed: transport failure for tenantId={} — {} — seeding can be re-triggered via portal",
                tenantId, ex.getMessage());
            return Optional.empty();
        }
    }
}
