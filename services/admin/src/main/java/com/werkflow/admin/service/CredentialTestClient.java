package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CredentialTestResultResponse;
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
 * Calls engine's {@code POST /api/internal/credentials/test} endpoint to verify a
 * tenant credential. The engine resolves the credential from OpenBao, invokes
 * the {@code CredentialType.validate} contract, and returns the outcome.
 *
 * <p>Plaintext values never traverse this client — only the
 * {@code (tenantId, credentialType, label)} triple goes outbound, and a
 * {@link CredentialTestResultResponse} comes back.
 *
 * <p>Authenticates as the {@code werkflow-admin} service account via the
 * {@code serviceRestTemplate} bean (Keycloak client_credentials grant).
 *
 * @see com.werkflow.admin.config.RestTemplateConfig#serviceRestTemplate
 */
@Component
@Slf4j
public class CredentialTestClient {

    private final RestTemplate restTemplate;
    private final String engineServiceUrl;

    public CredentialTestClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.engine-service.url:http://werkflow-engine:8081}") String engineServiceUrl) {
        this.restTemplate = restTemplate;
        this.engineServiceUrl = engineServiceUrl;
    }

    /**
     * Asks engine to validate the credential identified by the
     * {@code (tenantId, credentialType, label)} triple.
     *
     * @return engine's test outcome; on transport failure returns an error result
     *         with a generic message — detail goes to log only
     */
    public CredentialTestResultResponse test(String tenantId, String credentialType, String label) {
        String url = engineServiceUrl + "/api/internal/credentials/test";
        Map<String, String> body = Map.of(
            "tenantId", tenantId,
            "credentialType", credentialType,
            "label", label
        );
        try {
            ResponseEntity<CredentialTestResultResponse> response =
                restTemplate.postForEntity(url, body, CredentialTestResultResponse.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            log.warn("Credential test: unexpected response status={} for tenant={} type={} label={}",
                response.getStatusCode(), tenantId, credentialType, label);
            return new CredentialTestResultResponse(false, "Credential test failed");
        } catch (RestClientException ex) {
            log.error("Credential test transport failure for tenant={} type={} label={}",
                tenantId, credentialType, label, ex);
            return new CredentialTestResultResponse(false, "Credential test failed");
        }
    }
}
