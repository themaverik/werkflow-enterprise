package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.dto.CredentialPathDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry of {@link CredentialType} implementations and the runtime
 * resolver that delivers a typed {@link CredentialValues} to delegates.
 *
 * <h2>Resolution flow (M4.12 Phase B.2)</h2>
 * <ol>
 *   <li>If a {@code label} is supplied, ask admin for the OpenBao path via
 *       {@link CredentialMetadataClient}. On hit, read the secret from
 *       OpenBao via {@link VaultReader} and return.</li>
 *   <li>If admin returns 404 and the explicit-label path was taken, throw
 *       {@link CredentialResolutionException} — the caller specifically asked
 *       for that label.</li>
 *   <li>For the unlabelled overload, try {@code label="default"} first. On a
 *       404 there, fall back to the legacy env-property resolution (the B.1a
 *       behaviour) so tenants without per-instance credentials still work and
 *       Werkflow's system mail keeps flowing.</li>
 * </ol>
 *
 * <h2>Why two overloads</h2>
 * <ul>
 *   <li>{@link #resolveForTenant(String, String, String)} — explicit label,
 *       no env fallback. Used by delegate code that has been migrated to
 *       per-tenant credentials.</li>
 *   <li>{@link #resolveForTenant(String, String)} — convenience for legacy
 *       callers (e.g. system SMTP, B.1a tests) and the "no label specified
 *       in the BPMN" path. Tries the {@code default} label, then env.</li>
 * </ul>
 *
 * @see CredentialType
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
@Component
@Slf4j
public class CredentialRegistry {

    private static final String DEFAULT_LABEL = "default";

    private final Map<String, CredentialType> types;
    private final Environment env;
    private final CredentialMetadataClient metadataClient;
    private final VaultReader vaultReader;

    public CredentialRegistry(
            List<CredentialType> types,
            Environment env,
            CredentialMetadataClient metadataClient,
            VaultReader vaultReader) {
        this.types = types.stream()
            .collect(Collectors.toUnmodifiableMap(CredentialType::name, Function.identity()));
        this.env = env;
        this.metadataClient = metadataClient;
        this.vaultReader = vaultReader;
    }

    /**
     * Returns the {@link CredentialType} registered under {@code name}.
     *
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

    /** Returns the set of all registered credential type names. */
    public Set<String> registeredTypes() {
        return types.keySet();
    }

    /**
     * Convenience resolver for callers that have no specific label in mind
     * (legacy delegates, system SMTP, the B.1a test suite).
     *
     * <p>Behaviour:
     * <ol>
     *   <li>Try {@code label="default"} via {@link #resolveForTenant(String, String, String)}.</li>
     *   <li>On {@link CredentialResolutionException} (i.e. no DB row for the default label),
     *       fall back to env-based resolution.</li>
     * </ol>
     *
     * @throws IllegalArgumentException if the credential type is not registered
     */
    public CredentialValues resolveForTenant(String credentialTypeName, String tenantId) {
        get(credentialTypeName);
        try {
            return resolveForTenant(credentialTypeName, tenantId, DEFAULT_LABEL);
        } catch (CredentialResolutionException ex) {
            log.debug("No tenant credential for type={} tenant={} (label={}); falling back to env",
                credentialTypeName, tenantId, DEFAULT_LABEL);
            return resolveFromEnv(credentialTypeName);
        }
    }

    /**
     * Resolves a credential by explicit label. No env fallback — if the
     * caller asked for a specific label, an absent metadata row means a
     * deployment problem, not a "use the global one" intention.
     *
     * @throws IllegalArgumentException        if the credential type is not registered
     * @throws CredentialResolutionException   if no metadata row exists for the
     *                                         triple, or the Vault path is missing,
     *                                         or OpenBao is unreachable
     */
    public CredentialValues resolveForTenant(
            String credentialTypeName, String tenantId, String label) {
        get(credentialTypeName);

        Optional<CredentialPathDto> path = metadataClient.resolvePath(
            tenantId, credentialTypeName, label);
        if (path.isEmpty()) {
            throw new CredentialResolutionException(
                "No credential registered for tenant=" + tenantId
                    + " type=" + credentialTypeName + " label=" + label);
        }

        Map<String, Object> values;
        try {
            values = vaultReader.read(path.get().vaultPath())
                .orElseThrow(() -> new CredentialResolutionException(
                    "Credential metadata exists but Vault has no entry at path="
                        + path.get().vaultPath()));
        } catch (CredentialResolutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CredentialResolutionException(
                "OpenBao read failed for tenant=" + tenantId + " type=" + credentialTypeName
                    + " label=" + label, ex);
        }
        return CredentialValues.of(values);
    }

    // -- B.1a env-based fallback (preserved verbatim from the prior implementation) ---

    private CredentialValues resolveFromEnv(String credentialTypeName) {
        return switch (credentialTypeName) {
            case "smtp" -> resolveSmtp();
            case "slack-bot-token" -> resolveSlack();
            case "whatsapp-cloud-api" -> resolveWhatsApp();
            default -> CredentialValues.of(Map.of());
        };
    }

    private CredentialValues resolveSmtp() {
        Map<String, Object> map = new HashMap<>();
        putIfPresent(map, "host", "spring.mail.host");
        putIfPresent(map, "port", "spring.mail.port");
        putIfPresent(map, "username", "spring.mail.username");
        putIfPresent(map, "password", "spring.mail.password");
        putIfPresent(map, "useTls", "spring.mail.properties.mail.smtp.starttls.enable");
        return CredentialValues.of(map);
    }

    private CredentialValues resolveSlack() {
        Map<String, Object> map = new HashMap<>();
        putIfPresent(map, "botToken", "app.notification.slack.bot-token");
        putIfPresent(map, "signingSecret", "app.notification.slack.signing-secret");
        return CredentialValues.of(map);
    }

    private CredentialValues resolveWhatsApp() {
        Map<String, Object> map = new HashMap<>();
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
