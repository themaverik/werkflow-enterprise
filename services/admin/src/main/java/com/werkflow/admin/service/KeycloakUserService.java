package com.werkflow.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    /**
     * Creates a Keycloak user, assigns one realm role, and sends the invite email.
     * The KC username is set to {@code email} — consistent with how preferred_username
     * is used as the lookup key in the admin DB (keycloak_id = preferred_username = email).
     *
     * @param email     the user's email; also becomes the KC username/preferred_username
     * @param firstName first name
     * @param lastName  last name
     * @param tenantId  value to set as the 'tenant_id' KC user attribute
     * @param roleName  realm role to assign (e.g. "admin", "employee")
     * @throws IllegalStateException if KC user creation, role lookup, or role assignment fails
     */
    public void createKeycloakUser(
            String email, String firstName, String lastName,
            String tenantId, String roleName) {
        String token = fetchServiceAccountToken();

        String createUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users";
        Map<String, Object> userRepresentation = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName != null ? firstName : "",
                "lastName", lastName != null ? lastName : "",
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("tenant_id", List.of(tenantId)),
                "requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        ResponseEntity<Void> createResponse = restTemplate.exchange(
                createUrl, HttpMethod.POST,
                new HttpEntity<>(userRepresentation, headers), Void.class);

        if (!createResponse.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Keycloak user creation failed with status: " + createResponse.getStatusCode());
        }

        String userId = findKeycloakUserIdByEmail(email, token);
        assignRealmRole(userId, roleName, token);

        String actionsEmailUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                + "/users/" + userId + "/execute-actions-email";
        try {
            restTemplate.exchange(actionsEmailUrl, HttpMethod.PUT,
                    new HttpEntity<>(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"), headers), Void.class);
        } catch (Exception e) {
            log.warn("Failed to send invite email to {} (SMTP may not be configured): {}", email, e.getMessage());
        }

        log.info("Keycloak user created: email={}, tenantId={}, role={}", email, tenantId, roleName);
    }

    /**
     * Creates a Keycloak user for a new tenant's initial admin.
     * Delegates to {@link #createKeycloakUser} with the hardcoded realm role "admin".
     *
     * @param email     the new user's email (also used as username)
     * @param firstName the new user's first name
     * @param lastName  the new user's last name
     * @param tenantId  the tenant code to set as the tenant_id KC attribute
     */
    public void createTenantAdminUser(String email, String firstName, String lastName, String tenantId) {
        createKeycloakUser(email, firstName, lastName, tenantId, "admin");
    }

    @SuppressWarnings("unchecked")
    private String findKeycloakUserIdByEmail(String email, String token) {
        String searchUrl = UriComponentsBuilder
                .fromHttpUrl(keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users")
                .queryParam("email", email)
                .queryParam("exact", "true")
                .build()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                searchUrl, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        List<Map<String, Object>> users = response.getBody();
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("Keycloak user not found after creation: email=" + email);
        }
        return (String) users.get(0).get("id");
    }

    @SuppressWarnings("unchecked")
    private void assignRealmRole(String userId, String roleName, String token) {
        // Fetch the role representation by name
        String roleUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/roles/" + roleName;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> roleResponse = restTemplate.exchange(
                roleUrl, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> role = roleResponse.getBody();
        if (role == null) {
            throw new IllegalStateException("Keycloak role not found: " + roleName);
        }

        // Assign role to user
        String assignUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                + "/users/" + userId + "/role-mappings/realm";
        HttpHeaders assignHeaders = new HttpHeaders();
        assignHeaders.setContentType(MediaType.APPLICATION_JSON);
        assignHeaders.setBearerAuth(token);
        restTemplate.exchange(assignUrl, HttpMethod.POST,
                new HttpEntity<>(List.of(role), assignHeaders), Void.class);
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
