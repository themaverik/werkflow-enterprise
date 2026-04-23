package com.werkflow.engine.listener;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a Keycloak username to an email address.
 * Results are cached in-memory for the lifetime of the application.
 */
@Slf4j
@Component
public class UserEmailResolver {

    private final Keycloak keycloak;
    private final String realm;
    private final ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public UserEmailResolver(
        Keycloak keycloak,
        @Value("${keycloak.realm:werkflow}") String realm
    ) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * Resolves a username to an email address using Keycloak's exact-match search.
     * Returns {@link Optional#empty()} if the username is null/blank or not found.
     * Results are cached — Keycloak is called at most once per username per process lifetime.
     */
    public Optional<String> resolveEmail(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(username, this::lookupEmail);
    }

    private Optional<String> lookupEmail(String username) {
        try {
            List<UserRepresentation> users =
                keycloak.realm(realm).users().searchByUsername(username, true);
            if (users.isEmpty()) {
                log.debug("UserEmailResolver: no Keycloak user found for username='{}'", username);
                return Optional.empty();
            }
            String email = users.get(0).getEmail();
            log.debug("UserEmailResolver: resolved username='{}' → email='{}'", username, email);
            return Optional.ofNullable(email);
        } catch (Exception e) {
            log.warn("UserEmailResolver: failed to resolve email for username='{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }
}
