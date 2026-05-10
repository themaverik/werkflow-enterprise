package com.werkflow.admin.designtime.platform.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
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
     * Returns null on failure so callers can degrade gracefully.
     */
    public String getDmnDefinitionXml(String tenantId, String dmnKey) {
        try {
            String url = engineBaseUrl + "/api/v1/dmn/decisions/{key}/xml?tenantId={tenantId}";
            return restTemplate.getForObject(url, String.class, dmnKey, tenantId);
        } catch (Exception e) {
            log.warn("EngineClient: could not fetch DMN XML for key='{}' tenant='{}' — {}",
                    dmnKey, tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns Tier 1 YAML role→groups map from the engine config endpoint.
     * Returns an empty map on failure so PSS degrades to Tier 2 only.
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getTier1RoleMappings() {
        try {
            String url = engineBaseUrl + "/api/v1/config/flowable-role-mappings";
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE).getBody();
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
        } catch (Exception e) {
            log.warn("EngineClient: could not fetch Tier 1 role mappings — {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
