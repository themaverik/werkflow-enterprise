package com.werkflow.engine.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class AdminServiceClient {

    private final RestTemplate restTemplate;
    private final String adminServiceUrl;

    public AdminServiceClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.admin-service.url:http://localhost:8083}") String adminServiceUrl) {
        this.restTemplate = restTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Cacheable(value = "userProfiles", key = "#keycloakId + ':' + #tenantCode")
    public UserProfileDto getUserProfile(String keycloakId, String tenantCode) {
        String url = adminServiceUrl + "/api/internal/users/{keycloakId}/profile?tenantCode={tenantCode}";
        try {
            return restTemplate.getForObject(url, UserProfileDto.class, keycloakId, tenantCode);
        } catch (Exception e) {
            log.warn("AdminServiceClient: failed to fetch user profile for {}/{} — {}",
                keycloakId, tenantCode, e.getMessage());
            return null;
        }
    }

    @Cacheable(value = "configVars", key = "#tenantCode")
    public Map<String, String> getConfigVars(String tenantCode) {
        String url = adminServiceUrl + "/api/v1/config/vars/map?tenantCode={tenantCode}";
        try {
            ResponseEntity<Map<String, String>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, String>>() {}, tenantCode);
            return resp.getBody() != null ? resp.getBody() : Map.of();
        } catch (Exception e) {
            log.warn("AdminServiceClient: failed to fetch config vars for {} — {}", tenantCode, e.getMessage());
            return Map.of();
        }
    }

    @Cacheable(value = "roleMappings", key = "#tenantCode")
    public Map<String, List<String>> getRoleMappings(String tenantCode) {
        String url = adminServiceUrl + "/api/v1/config/role-mappings/by-role?tenantCode={tenantCode}";
        try {
            ResponseEntity<Map<String, List<String>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, List<String>>>() {}, tenantCode);
            return resp.getBody() != null ? resp.getBody() : Map.of();
        } catch (Exception e) {
            log.warn("AdminServiceClient: failed to fetch role mappings for {} — {}", tenantCode, e.getMessage());
            return Map.of();
        }
    }

    @Cacheable(value = "tenantConfig", key = "'rolePerms:' + #tenantCode + ':' + #roleNames")
    public Set<String> getTenantRolePermissions(String tenantCode, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return Set.of();
        String roles = String.join(",", roleNames);
        String url = adminServiceUrl + "/api/internal/tenants/{tenantCode}/role-permissions?roles={roles}";
        try {
            ResponseEntity<Set<String>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Set<String>>() {}, tenantCode, roles);
            return resp.getBody() != null ? resp.getBody() : Set.of();
        } catch (Exception e) {
            log.warn("AdminServiceClient: failed to fetch tenant role perms for {} — {}", tenantCode, e.getMessage());
            return Set.of();
        }
    }
}
