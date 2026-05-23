package com.werkflow.admin.service;

import com.werkflow.admin.config.JpaAuditingConfig;
import com.werkflow.admin.dto.credential.CreateTenantCredentialRequest;
import com.werkflow.admin.dto.credential.TenantCredentialResponse;
import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.integration.AbstractCredentialIT;
import com.werkflow.admin.repository.TenantCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantCredentialService} driven against both a real
 * Postgres container (via {@link AbstractCredentialIT}) and a real OpenBao container.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Real Postgres → real {@code TenantCredentialRepository} (autowired via {@code @DataJpaTest}).</li>
 *   <li>Real OpenBao → real {@link VaultCredentialStore} built from {@link #newVaultStore()}.</li>
 *   <li>{@link CredentialTestClient} → Mockito mock (no engine in tests).</li>
 * </ul>
 *
 * <p>{@code @DataJpaTest} wraps each test in a transaction that rolls back at end, giving
 * free Postgres cleanup. Vault paths are disambiguated per test by unique tenantIds.
 * {@code @Import(JpaAuditingConfig.class)} supplies the {@code offsetDateTimeProvider}
 * bean required by {@code AdminServiceApplication}'s {@code @EnableJpaAuditing} reference.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TenantCredentialServiceIT extends AbstractCredentialIT {

    @Autowired
    TenantCredentialRepository repository;

    VaultCredentialStore realVault;
    CredentialTestClient mockTestClient;

    @BeforeEach
    void setUp() {
        realVault = newVaultStore();
        mockTestClient = mock(CredentialTestClient.class);
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create writes values to Vault and persists metadata; delete removes both")
    void create_writesToVault_andPersistsMetadata() {
        String tenantId = "tenant-" + UUID.randomUUID();
        String path = "tenants/" + tenantId + "/smtp/default";
        Map<String, Object> values = Map.of("host", "smtp.example.com", "password", "s3cr3t");

        TenantCredentialService service = new TenantCredentialService(repository, realVault, mockTestClient);
        TenantCredentialResponse response = service.create(tenantId,
            new CreateTenantCredentialRequest("smtp", "default", values));

        // Response metadata
        assertThat(response.fieldNames()).containsExactlyInAnyOrder("host", "password");
        assertThat(response.credentialType()).isEqualTo("smtp");

        // DB row persisted
        Optional<TenantCredential> dbRow = repository.findByTenantIdAndCredentialTypeAndLabel(
            tenantId, "smtp", "default");
        assertThat(dbRow).isPresent();
        UUID savedId = dbRow.get().getId();

        // Vault entry present with correct payload
        Optional<Map<String, Object>> vaultData = realVault.read(path);
        assertThat(vaultData).isPresent();
        assertThat(vaultData.get()).containsEntry("host", "smtp.example.com");
        assertThat(vaultData.get()).containsEntry("password", "s3cr3t");

        // delete removes both
        service.delete(tenantId, savedId);
        assertThat(realVault.read(path)).isEmpty();
        assertThat(repository.findById(savedId)).isEmpty();
    }

    @Test
    @DisplayName("delete removes the Vault entry and the DB row")
    void delete_removesVaultEntry() {
        String tenantId = "tenant-" + UUID.randomUUID();
        String path = "tenants/" + tenantId + "/slack-bot-token/alpha";

        TenantCredentialService service = new TenantCredentialService(repository, realVault, mockTestClient);
        TenantCredentialResponse response = service.create(tenantId,
            new CreateTenantCredentialRequest(
                "slack-bot-token", "alpha",
                Map.of("botToken", "xoxb-abc", "signingSecret", "shh")));

        UUID savedId = repository.findByTenantIdAndCredentialTypeAndLabel(
            tenantId, "slack-bot-token", "alpha").orElseThrow().getId();

        service.delete(tenantId, savedId);

        assertThat(realVault.read(path)).isEmpty();
        assertThat(repository.findById(savedId)).isEmpty();
    }

    @Test
    @DisplayName("create compensates by deleting the Vault entry when the DB insert fails")
    void create_compensation_deletesVaultOnDbFailure() {
        String tenantId = "tenant-" + UUID.randomUUID();
        String path = "tenants/" + tenantId + "/smtp/default";

        // Use a mock repository so we can force a deterministic DB failure.
        // The real value of this test is proving that the real VaultCredentialStore.delete
        // was actually called (i.e. the vault entry is gone after the compensation runs).
        TenantCredentialRepository mockRepo = mock(TenantCredentialRepository.class);
        when(mockRepo.existsByTenantIdAndCredentialTypeAndLabel(any(), any(), any()))
            .thenReturn(false);
        when(mockRepo.save(any(TenantCredential.class)))
            .thenThrow(new RuntimeException("constraint violation"));

        TenantCredentialService service = new TenantCredentialService(mockRepo, realVault, mockTestClient);

        assertThatThrownBy(() -> service.create(tenantId,
            new CreateTenantCredentialRequest("smtp", "default",
                Map.of("host", "smtp.example.com"))))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("constraint violation");

        // Real Vault compensation must have deleted the entry written before the DB call.
        assertThat(realVault.read(path)).isEmpty();
    }
}
