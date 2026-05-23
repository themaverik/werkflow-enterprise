package com.werkflow.admin.integration;

import com.werkflow.admin.service.VaultCredentialStore;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;

/**
 * Shared singleton container base for tenant-credential integration tests.
 *
 * <p>Both containers are started once in a static initializer and reused across
 * all subclasses. Ryuk handles teardown after the JVM exits.
 *
 * <p>Subclasses that load a Spring context (i.e. use {@code @DataJpaTest}) will
 * pick up the Postgres connection properties via the {@link #overrideProperties}
 * {@code @DynamicPropertySource} method. Subclasses that build objects manually
 * (no Spring context) can call {@link #newVaultStore()} directly.
 */
public abstract class AbstractCredentialIT {

    protected static final String VAULT_TOKEN = "root";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    // OpenBao uses BAO_DEV_* env vars (not VAULT_DEV_*) to set the root token and listen address.
    // VAULT_DEV_ROOT_TOKEN_ID generates a random token that we cannot predict; BAO_DEV_ROOT_TOKEN_ID
    // pins it to the value we supply so newVaultStore() can authenticate.
    @SuppressWarnings("resource")
    static final GenericContainer<?> OPENBAO =
        new GenericContainer<>(DockerImageName.parse("openbao/openbao:latest"))
            .withExposedPorts(8200)
            .withEnv("BAO_DEV_ROOT_TOKEN_ID", "root")
            .withEnv("BAO_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withCommand("server", "-dev")
            .waitingFor(
                Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200));

    static {
        POSTGRES.start();
        OPENBAO.start();
    }

    /** Mapped base URL for the OpenBao container. */
    protected static String vaultAddr() {
        return "http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(8200);
    }

    /**
     * Builds a real {@link VaultCredentialStore} wired to the singleton container.
     * Intended for use in tests that construct service objects without a Spring context.
     */
    protected static VaultCredentialStore newVaultStore() {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(vaultAddr()));
        VaultTemplate template = new VaultTemplate(endpoint, new TokenAuthentication(VAULT_TOKEN));
        return new VaultCredentialStore(template.opsForVersionedKeyValue("secret"));
    }

    /**
     * Registers Postgres and Flyway/JPA properties for any Spring context loaded by a subclass.
     * The schema property must be set so Flyway creates {@code admin_service} before Hibernate
     * validates against it.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "admin_service");
        registry.add("spring.flyway.default-schema", () -> "admin_service");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "admin_service");
        // Suppress Vault config requirements — tests build VaultCredentialStore manually.
        registry.add("werkflow.vault.addr", AbstractCredentialIT::vaultAddr);
        registry.add("werkflow.vault.token", () -> VAULT_TOKEN);
    }
}
