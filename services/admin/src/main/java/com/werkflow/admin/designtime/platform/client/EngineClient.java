package com.werkflow.admin.designtime.platform.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the engine service — used only to fetch Tier 1 YAML role mappings.
 * Degrades gracefully when engine is unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineClient {

    private final RestTemplate restTemplate;

    @Value("${app.engine-service.url:http://werkflow-engine:8081}")
    private String engineBaseUrl;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    /**
     * Fetches the raw DMN XML for a deployed decision by its key and tenant.
     * <p>The engine endpoint requires an authenticated caller with the
     * {@code WORKFLOW:MANAGE} permission, so the caller's bearer token must be
     * threaded through; an unauthenticated call is rejected with 401.
     * Returns null on failure so callers can degrade gracefully.
     */
    public String getDmnDefinitionXml(String tenantId, String dmnKey, String bearerToken) {
        try {
            String url = engineBaseUrl + "/api/v1/dmn/decisions/{key}/xml?tenantId={tenantId}";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            return restTemplate.exchange(url, HttpMethod.GET, entity, String.class, dmnKey, tenantId).getBody();
        } catch (Exception e) {
            log.warn("EngineClient: could not fetch DMN XML for key='{}' tenant='{}' — {}",
                    dmnKey, tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns Tier 1 YAML role→groups map from the engine config endpoint.
     * <p>The engine exposes {@code /api/v1/config/flowable-role-mappings} as
     * {@code permitAll()}, so this call is intentionally unauthenticated (an explicit
     * empty entity, never a {@code null} request). If that endpoint is ever secured,
     * the call would be rejected with 401/403 and PSS would silently degrade to Tier 2
     * only — so an auth rejection is logged at ERROR (distinct from the engine-unreachable
     * WARN) to surface the misconfiguration loudly. The remedy at that point is to thread
     * the caller's bearer token here, as {@link #getDmnDefinitionXml} already does.
     * Returns an empty map on failure so PSS degrades to Tier 2 only.
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getTier1RoleMappings() {
        String url = engineBaseUrl + "/api/v1/config/flowable-role-mappings";
        try {
            Map<String, Object> response =
                    restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, MAP_TYPE).getBody();
            if (response == null) return Map.of();
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) response.get("mappings");
            if (mappings == null) return Map.of();
            Map<String, List<String>> result = new java.util.LinkedHashMap<>();
            for (Map<String, Object> entry : mappings) {
                String role = (String) entry.get("role");
                List<String> groups = (List<String>) entry.get("groups");
                if (role != null && groups != null) result.put(role, groups);
            }
            return result;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("EngineClient: engine rejected the unauthenticated role-mappings call ({}). "
                    + "The endpoint appears to have been secured — PSS is degrading to Tier 2 only. "
                    + "Thread the caller's bearer token here (see getDmnDefinitionXml).", e.getStatusCode());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("EngineClient: could not fetch Tier 1 role mappings — {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
