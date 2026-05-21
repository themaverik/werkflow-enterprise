package com.werkflow.engine.action.credential;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry of all known {@link CredentialType} implementations.
 *
 * <p>Spring collects every {@link CredentialType} bean in the context and injects them as a
 * {@code List<CredentialType>}. The registry indexes them by {@link CredentialType#name()} for
 * O(1) lookup.
 *
 * <h2>B.1a credential resolution (environment-based)</h2>
 * {@link #resolveForTenant} reads credentials from Spring {@link Environment} properties.
 * The {@code tenantId} parameter is accepted now so call-sites won't change when Phase B.2
 * replaces this with a DB-backed implementation.
 *
 * <p>When a property is absent the corresponding key is simply omitted from the returned
 * {@link CredentialValues} — {@link CredentialValues#getString} will return {@code null}.
 *
 * @see CredentialType
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
@Component
public class CredentialRegistry {

    private final Map<String, CredentialType> types;
    private final Environment env;

    public CredentialRegistry(List<CredentialType> types, Environment env) {
        this.types = types.stream()
            .collect(Collectors.toUnmodifiableMap(CredentialType::name, Function.identity()));
        this.env = env;
    }

    /**
     * Returns the {@link CredentialType} registered under {@code name}.
     *
     * @param name canonical credential type name (e.g. {@code "smtp"})
     * @throws IllegalArgumentException if no type with that name is registered
     */
    public CredentialType get(String name) {
        CredentialType found = types.get(name);
        if (found == null) {
            throw new IllegalArgumentException(
                "Unknown credential type: '" + name + "'. Registered types: " + types.keySet());
        }
        return found;
    }

    /**
     * Returns the set of all registered credential type names.
     */
    public Set<String> registeredTypes() {
        return types.keySet();
    }

    /**
     * Resolves credential values for the given type and tenant.
     *
     * <p><b>B.1a behaviour (global, env-only):</b> the {@code tenantId} is accepted but
     * ignored — all tenants share the same Spring-property-backed credentials. Phase B.2
     * will rewrite this method to query the {@code tenant_credentials} table and fall back
     * to env properties only when no DB row exists.
     *
     * <p>Property paths resolved per type:
     * <ul>
     *   <li>{@code "smtp"} — {@code spring.mail.*} + {@code spring.mail.properties.mail.smtp.starttls.enable}</li>
     *   <li>{@code "slack-bot-token"} — {@code app.notification.slack.*}</li>
     *   <li>{@code "whatsapp-cloud-api"} — {@code app.notification.whatsapp.*}</li>
     * </ul>
     *
     * @param credentialTypeName canonical type name; must be registered
     * @param tenantId           tenant context (ignored in B.1a)
     * @return populated {@link CredentialValues}; absent properties are simply not in the map
     * @throws IllegalArgumentException if the credential type name is not registered
     */
    public CredentialValues resolveForTenant(String credentialTypeName, String tenantId) {
        // Validates that the type is known; throws if not.
        get(credentialTypeName);

        return switch (credentialTypeName) {
            case "smtp" -> resolveSmtp();
            case "slack-bot-token" -> resolveSlack();
            case "whatsapp-cloud-api" -> resolveWhatsApp();
            default -> CredentialValues.of(Map.of());
        };
    }

    // -- private env resolution helpers --

    private CredentialValues resolveSmtp() {
        Map<String, Object> map = new java.util.HashMap<>();
        putIfPresent(map, "host", "spring.mail.host");
        putIfPresent(map, "port", "spring.mail.port");
        putIfPresent(map, "username", "spring.mail.username");
        putIfPresent(map, "password", "spring.mail.password");
        putIfPresent(map, "useTls", "spring.mail.properties.mail.smtp.starttls.enable");
        return CredentialValues.of(map);
    }

    private CredentialValues resolveSlack() {
        Map<String, Object> map = new java.util.HashMap<>();
        putIfPresent(map, "botToken", "app.notification.slack.bot-token");
        putIfPresent(map, "signingSecret", "app.notification.slack.signing-secret");
        return CredentialValues.of(map);
    }

    private CredentialValues resolveWhatsApp() {
        Map<String, Object> map = new java.util.HashMap<>();
        putIfPresent(map, "accessToken", "app.notification.whatsapp.access-token");
        putIfPresent(map, "phoneNumberId", "app.notification.whatsapp.phone-number-id");
        putIfPresent(map, "apiVersion", "app.notification.whatsapp.api-version");
        return CredentialValues.of(map);
    }

    private void putIfPresent(Map<String, Object> map, String key, String propertyPath) {
        String value = env.getProperty(propertyPath);
        if (value != null) {
            map.put(key, value);
        }
    }
}
