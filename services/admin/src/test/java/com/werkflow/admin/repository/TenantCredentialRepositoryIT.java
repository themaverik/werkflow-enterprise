package com.werkflow.admin.repository;

import com.werkflow.admin.config.JpaAuditingConfig;
import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.integration.AbstractCredentialIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link TenantCredentialRepository} using a real Postgres
 * container (Flyway-created schema) via {@link AbstractCredentialIT}.
 *
 * <p>{@code @DataJpaTest} is a slice test: it picks up {@code AdminServiceApplication}
 * (which carries {@code @EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")}),
 * but {@code @Configuration} beans are excluded from the slice by default. We must
 * {@code @Import(JpaAuditingConfig.class)} so the {@code offsetDateTimeProvider} bean is
 * present and {@code created_at}/{@code updated_at} (NOT NULL) are populated on save.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TenantCredentialRepositoryIT extends AbstractCredentialIT {

    @Autowired
    TenantCredentialRepository repository;

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save then findByTenantIdAndCredentialTypeAndLabel returns the row with generated id and audit timestamps")
    void save_then_findByTenantIdAndType_returnsPersistedRow() {
        TenantCredential entity = new TenantCredential(
            "tenant-repo-1", "smtp", "default", "tenants/tenant-repo-1/smtp/default");

        repository.saveAndFlush(entity);

        var found = repository.findByTenantIdAndCredentialTypeAndLabel(
            "tenant-repo-1", "smtp", "default");

        assertThat(found).isPresent();
        TenantCredential row = found.get();
        assertThat(row.getId()).isNotNull();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getUpdatedAt()).isNotNull();
        assertThat(row.getCredentialType()).isEqualTo("smtp");
        assertThat(row.getLabel()).isEqualTo("default");
        assertThat(row.getVaultPath()).isEqualTo("tenants/tenant-repo-1/smtp/default");
    }

    @Test
    @DisplayName("unique constraint rejects a second row with the same (tenantId, credentialType, label)")
    void uniqueConstraint_rejectsDuplicateTriple() {
        TenantCredential first = new TenantCredential(
            "tenant-repo-2", "smtp", "default", "tenants/tenant-repo-2/smtp/default");
        repository.saveAndFlush(first);

        TenantCredential duplicate = new TenantCredential(
            "tenant-repo-2", "smtp", "default", "tenants/tenant-repo-2/smtp/default");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByTenantIdAndCredentialTypeAndLabel returns true after save, false for a different label")
    void existsByTenantIdAndCredentialTypeAndLabel_correctResults() {
        TenantCredential entity = new TenantCredential(
            "tenant-repo-3", "slack-bot-token", "alpha",
            "tenants/tenant-repo-3/slack-bot-token/alpha");
        repository.saveAndFlush(entity);

        assertThat(repository.existsByTenantIdAndCredentialTypeAndLabel(
            "tenant-repo-3", "slack-bot-token", "alpha")).isTrue();
        assertThat(repository.existsByTenantIdAndCredentialTypeAndLabel(
            "tenant-repo-3", "slack-bot-token", "beta")).isFalse();
    }

    @Test
    @DisplayName("findByTenantId returns only that tenant's rows; other tenant rows are not included")
    void findByTenantId_isolatesTenantRows() {
        repository.saveAndFlush(new TenantCredential(
            "tenant-repo-4a", "smtp", "default", "tenants/tenant-repo-4a/smtp/default"));
        repository.saveAndFlush(new TenantCredential(
            "tenant-repo-4a", "slack-bot-token", "alpha",
            "tenants/tenant-repo-4a/slack-bot-token/alpha"));
        repository.saveAndFlush(new TenantCredential(
            "tenant-repo-4b", "smtp", "default", "tenants/tenant-repo-4b/smtp/default"));

        List<TenantCredential> rows = repository.findByTenantId("tenant-repo-4a");

        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.getTenantId().equals("tenant-repo-4a"));
    }
}
