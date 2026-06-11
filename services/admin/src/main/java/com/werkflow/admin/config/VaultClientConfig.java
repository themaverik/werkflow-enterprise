package com.werkflow.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;

import java.net.URI;

/**
 * Spring Vault wiring for the OpenBao secrets backend.
 *
 * <p>Admin-service uses the {@code werkflow-admin} OpenBao token (read+write+delete on
 * {@code secret/data/tenants/+/+/+}) to provision and rotate per-tenant credentials
 * for M4.12 Phase B.2. See ADR-020 and
 * {@code docs/brainstorm/Brainstorm-Credential-Registry-DB.md}.
 *
 * <p>Configuration sources (in order of precedence):
 * <ol>
 *   <li>{@code WERKFLOW_VAULT_ADDR} env var (relaxed-bound to {@code werkflow.vault.addr})</li>
 *   <li>{@code WERKFLOW_VAULT_TOKEN} env var (relaxed-bound to {@code werkflow.vault.token})</li>
 * </ol>
 *
 * <p>The KV-v2 secret engine is expected to be mounted at {@code secret} (OpenBao default in
 * dev mode; the {@code openbao-init} container guarantees this in our compose stack).
 */
@Slf4j
@Configuration
public class VaultClientConfig {

    private final String vaultAddr;
    private final String vaultToken;
    private final String appEnvironment;

    public VaultClientConfig(
            @Value("${werkflow.vault.addr}") String vaultAddr,
            @Value("${werkflow.vault.token}") String vaultToken,
            @Value("${app.environment:production}") String appEnvironment
    ) {
        this.vaultAddr = vaultAddr;
        this.vaultToken = vaultToken;
        this.appEnvironment = appEnvironment;
    }

    @jakarta.annotation.PostConstruct
    public void assertProductionSafety() {
        if (vaultToken.endsWith("-do-not-use-in-prod")) {
            if (!"development".equalsIgnoreCase(appEnvironment)) {
                throw new IllegalStateException(
                    "OpenBao dev token detected in non-development environment (" + appEnvironment + "). " +
                    "Set WERKFLOW_VAULT_TOKEN to a production token.");
            }
            log.warn("VaultClientConfig: using dev OpenBao token — ensure this is a development environment");
        }
    }

    @Bean
    public VaultEndpoint vaultEndpoint() {
        return VaultEndpoint.from(URI.create(vaultAddr));
    }

    @Bean
    public ClientAuthentication clientAuthentication() {
        return new TokenAuthentication(vaultToken);
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint endpoint, ClientAuthentication auth) {
        return new VaultTemplate(endpoint, auth);
    }

    /**
     * KV-v2 operations bound to the {@code secret} mount.
     * Use this for all reads/writes of tenant credentials.
     */
    @Bean
    public VaultVersionedKeyValueOperations tenantCredentialKvOperations(VaultTemplate vaultTemplate) {
        return vaultTemplate.opsForVersionedKeyValue("secret");
    }
}
