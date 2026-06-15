package com.werkflow.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for Keycloak Admin API operations.
 *
 * <p>IMPORTANT: The werkflow-portal service account requires the 'manage-users' role
 * in the realm-management client for the setKcUserEnabled/updateKcUserName calls.
 * Grant via: KC Admin → werkflow realm → Clients → werkflow-portal → Service Account Roles
 * → realm-management → manage-users
 */
@Service
@Slf4j
public class KeycloakUserService {

    public static final String KC_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";
    public static final String KC_ACTION_VERIFY_EMAIL    = "VERIFY_EMAIL";

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
                "requiredActions", List.of(KC_ACTION_UPDATE_PASSWORD, KC_ACTION_VERIFY_EMAIL)
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
                    new HttpEntity<>(List.of(KC_ACTION_UPDATE_PASSWORD, KC_ACTION_VERIFY_EMAIL), headers), Void.class);
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

    /**
     * Deletes a Keycloak user by email (keycloakId = email = preferred_username).
     *
     * <p>Non-fatal: if the KC user is not found (already removed manually), or if KC is
     * unreachable, a warning is logged and the method returns normally. The DB deletion
     * has already succeeded at the call site.
     *
     * @param email the user's email, which is also their KC username / keycloak_id in the DB
     */
    public void deleteKcUser(String email) {
        String token;
        try {
            token = fetchServiceAccountToken();
        } catch (Exception e) {
            log.warn("KC delete skipped — could not obtain service account token for email={}: {}", email, e.getMessage());
            return;
        }

        String kcUuid;
        try {
            kcUuid = findKeycloakUserIdByEmail(email, token);
        } catch (IllegalStateException e) {
            log.warn("KC delete skipped — user not found in KC (may have been removed manually): email={}", email);
            return;
        } catch (Exception e) {
            log.warn("KC delete skipped — error resolving KC UUID for email={}: {}", email, e.getMessage());
            return;
        }

        try {
            String deleteUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + kcUuid;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("KC user deleted: email={}, kcUuid={}", email, kcUuid);
        } catch (Exception e) {
            log.warn("KC delete failed for email={} (kcUuid={}): {}", email, kcUuid, e.getMessage());
        }
    }

    /**
     * Re-sends the invite email (UPDATE_PASSWORD + VERIFY_EMAIL required actions) to a user.
     *
     * @param email the user's email, which is also their KC username / keycloak_id in the DB
     * @throws org.springframework.web.server.ResponseStatusException 503 if KC is unreachable
     */
    public void resendInviteEmail(String email) {
        String token = fetchServiceAccountToken();

        String kcUuid = findKeycloakUserIdByEmail(email, token);

        String actionsEmailUrl = keycloakAdminUrl + "/admin/realms/" + keycloakRealm
                + "/users/" + kcUuid + "/execute-actions-email";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        try {
            restTemplate.exchange(actionsEmailUrl, HttpMethod.PUT,
                    new HttpEntity<>(List.of(KC_ACTION_UPDATE_PASSWORD, KC_ACTION_VERIFY_EMAIL), headers), Void.class);
            log.info("Invite email resent: email={}", email);
        } catch (Exception e) {
            log.warn("KC execute-actions-email failed for email={}: {}", email, e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to contact identity provider");
        }
    }

    /**
     * Fetches the requiredActions list for a Keycloak user. Throws on KC error (fail-closed).
     */
    public List<String> getKcRequiredActions(String keycloakId) {
        String token = fetchServiceAccountToken();
        String url = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + keycloakId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> body = response.getBody();
        if (body == null) return List.of();
        Object actions = body.get("requiredActions");
        if (!(actions instanceof List)) return List.of();
        return ((List<?>) actions).stream()
                .filter(a -> a instanceof String)
                .map(a -> (String) a)
                .collect(Collectors.toList());
    }

    /**
     * Enables or disables a Keycloak user account.
     *
     * <p>KC Admin API PUT /users/{id} is a full replace — sending only {"enabled":false} would
     * null-out username, email, firstName, lastName and attributes on the KC record. We must
     * GET the current UserRepresentation first, mutate the enabled flag, then PUT the full body.
     */
    public void setKcUserEnabled(String keycloakId, boolean enabled) {
        String token = fetchServiceAccountToken();
        String url = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + keycloakId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Fetch the existing UserRepresentation so we can PUT a complete body.
        ResponseEntity<Map<String, Object>> getResponse = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> existing = getResponse.getBody();
        if (existing == null) {
            throw new IllegalStateException("KC user not found: keycloakId=" + keycloakId);
        }

        Map<String, Object> body = new java.util.HashMap<>(existing);
        body.put("enabled", enabled);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }

    /**
     * Updates first and last name for a Keycloak user.
     *
     * <p>KC Admin API PUT /users/{id} is a full replace — sending only {firstName, lastName}
     * would null-out username, email, enabled and attributes. We GET the current
     * UserRepresentation first, mutate only the name fields, then PUT the full body.
     */
    public void updateKcUserName(String keycloakId, String firstName, String lastName) {
        String token = fetchServiceAccountToken();
        String url = keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + keycloakId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Fetch the existing UserRepresentation so we can PUT a complete body.
        ResponseEntity<Map<String, Object>> getResponse = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> existing = getResponse.getBody();
        if (existing == null) {
            throw new IllegalStateException("KC user not found: keycloakId=" + keycloakId);
        }

        Map<String, Object> body = new java.util.HashMap<>(existing);
        body.put("firstName", firstName != null ? firstName : "");
        body.put("lastName", lastName != null ? lastName : "");
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
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

}
