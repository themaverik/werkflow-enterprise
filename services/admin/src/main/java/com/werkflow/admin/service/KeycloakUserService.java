package com.werkflow.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeycloakUserService {

    private static final Set<String> INTERNAL_ROLES = Set.of("offline_access", "uma_authorization");

    @Value("${app.keycloak.auth-server-url:http://localhost:8090}")
    private String keycloakAuthUrl;

    @Value("${app.keycloak.admin-url:http://localhost:8090}")
    private String keycloakAdminUrl;

    @Value("${app.keycloak.realm:werkflow}")
    private String keycloakRealm;

    @Value("${app.keycloak.client-id:werkflow-portal}")
    private String clientId;

    @Value("${app.keycloak.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public KeycloakUserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Lists all realm roles from Keycloak, excluding internal system roles.
     * Uses client credentials (werkflow-portal service account) to call the Admin API.
     */
    public List<String> listRealmRoles() {
        try {
            String token = fetchServiceAccountToken();
            return fetchRealmRoles(token);
        } catch (Exception e) {
            log.warn("Failed to fetch realm roles from Keycloak: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchServiceAccountToken() {
        String tokenUrl = keycloakAdminUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);
        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("No access_token in Keycloak token response");
        }
        return (String) response.get("access_token");
    }

    private List<String> fetchRealmRoles(String token) {
        String rolesUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/roles";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                rolesUrl, HttpMethod.GET, request,
                new ParameterizedTypeReference<>() {});
        if (response.getBody() == null) return List.of();
        return response.getBody().stream()
                .map(r -> (String) r.get("name"))
                .filter(name -> name != null
                        && !INTERNAL_ROLES.contains(name)
                        && !name.startsWith("default-roles-"))
                .sorted()
                .collect(Collectors.toList());
    }

    // ---- Legacy stub methods kept for backward compatibility ----

    public List<KeycloakUserInfo> findUsersByDoALevel(int doaLevel, String department) {
        log.debug("findUsersByDoALevel: doaLevel={}, department={}", doaLevel, department);
        List<KeycloakUserInfo> results = new ArrayList<>();
        if (doaLevel >= 1) {
            results.add(new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR"));
            results.add(new KeycloakUserInfo("manager2", "Jane Manager", "jmanager@company.com", 1, "Finance"));
        }
        if (doaLevel >= 2) {
            results.add(new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR"));
            results.add(new KeycloakUserInfo("fin_head", "Finance Head", "finhead@company.com", 2, "Finance"));
        }
        if (doaLevel >= 3) results.add(new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance"));
        if (doaLevel >= 4) results.add(new KeycloakUserInfo("ceo", "CEO", "ceo@company.com", 4, "Executive"));
        if (department != null && !department.isEmpty()) {
            results = results.stream()
                    .filter(u -> u.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return results;
    }

    public List<KeycloakUserInfo> findUsersByGroup(String groupName) {
        log.debug("findUsersByGroup: {}", groupName);
        List<KeycloakUserInfo> results = new ArrayList<>();
        switch (groupName.toLowerCase()) {
            case "department_managers" -> {
                results.add(new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR"));
                results.add(new KeycloakUserInfo("manager2", "Jane Manager", "jmanager@company.com", 1, "Finance"));
            }
            case "department_heads" -> {
                results.add(new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR"));
                results.add(new KeycloakUserInfo("fin_head", "Finance Head", "finhead@company.com", 2, "Finance"));
            }
            case "finance_approvers" -> results.add(new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance"));
            case "executive_approvers" -> results.add(new KeycloakUserInfo("ceo", "CEO", "ceo@company.com", 4, "Executive"));
        }
        return results;
    }

    public KeycloakUserInfo findUserByUsername(String username) {
        log.debug("findUserByUsername: {}", username);
        return switch (username) {
            case "manager1" -> new KeycloakUserInfo("manager1", "John Manager", "manager@company.com", 1, "HR");
            case "cfo" -> new KeycloakUserInfo("cfo", "CFO", "cfo@company.com", 3, "Finance");
            default -> null;
        };
    }

    public KeycloakUserInfo getManagerForUser(String username) {
        KeycloakUserInfo user = findUserByUsername(username);
        if (user == null) return null;
        return new KeycloakUserInfo("hr_head", "HR Head", "hrhead@company.com", 2, "HR");
    }

    public List<KeycloakUserInfo> findUsersByRole(String roleName) {
        log.debug("findUsersByRole: {}", roleName);
        if ("asset_approver".equals(roleName)) {
            return List.of(new KeycloakUserInfo("inv_manager", "Inventory Manager", "invmgr@company.com", 2, "Inventory"));
        }
        return List.of();
    }

    public boolean updateUserAttributes(String username, Map<String, String> attributes) {
        log.debug("updateUserAttributes: user={}, keys={}", username, attributes.keySet());
        return true;
    }

    public static class KeycloakUserInfo {
        private final String username;
        private final String displayName;
        private final String email;
        private final Integer doaLevel;
        private final String department;

        public KeycloakUserInfo(String username, String displayName, String email, Integer doaLevel, String department) {
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.doaLevel = doaLevel;
            this.department = department;
        }

        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public Integer getDoaLevel() { return doaLevel; }
        public String getDepartment() { return department; }
    }
}
