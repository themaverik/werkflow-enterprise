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
     * Results are cached in-memory only on successful lookup (email found).
     * Failures and not-found results are not cached, so transient Keycloak errors
     * do not permanently silence notifications for a username.
     */
    public Optional<String> resolveEmail(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Optional<String> cached = cache.get(username);
        if (cached != null) {
            return cached;
        }
        Optional<String> resolved = lookupEmail(username);
        if (resolved.isPresent()) {
            cache.putIfAbsent(username, resolved);
        }
        return resolved;
    }

    private Optional<String> lookupEmail(String username) {
        try {
            List<UserRepresentation> users =
                keycloak.realm(realm).users().searchByUsername(username, true);
            if (users.isEmpty()) {
                log.debug("no Keycloak user found for username='{}'", username);
                return Optional.empty();
            }
            String email = users.get(0).getEmail();
            log.debug("resolved username='{}' → '{}'", username, email);
            return Optional.ofNullable(email);
        } catch (Exception e) {
            log.warn("failed to resolve email for username='{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }
}
