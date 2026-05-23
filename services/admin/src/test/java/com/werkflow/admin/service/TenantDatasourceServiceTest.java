package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CredentialPathResponse;
import com.werkflow.admin.dto.datasource.DatasourceEngineConfig;
import com.werkflow.admin.dto.datasource.DatasourceTestResult;
import com.werkflow.admin.dto.datasource.TenantDatasourceRequest;
import com.werkflow.admin.dto.datasource.TenantDatasourceResponse;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.VaultException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDatasourceServiceTest {

    @Mock private TenantDatasourceRepository repository;
    @Mock private TenantCredentialService credentialService;
    @Mock private VaultCredentialStore vault;

    private TenantDatasourceService service;

    @BeforeEach
    void setUp() {
        service = new TenantDatasourceService(repository, credentialService, vault);
        ReflectionTestUtils.setField(service, "appEnvironment", "development");
    }

    private TenantDatasourceRequest request() {
        return new TenantDatasourceRequest(
            "ds1", "jdbc:h2:mem:t", "org.h2.Driver", "my-cred", "h2", 1, 5, 5, 600);
    }

    @Test
    @DisplayName("create persists credentialRef and never touches credential values")
    void create_setsCredentialRef() {
        when(repository.existsByTenantIdAndRef("acme", "ds1")).thenReturn(false);
        when(repository.save(any(TenantDatasource.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDatasourceResponse resp = service.create("acme", request());

        assertThat(resp.credentialRef()).isEqualTo("my-cred");
        assertThat(resp.ref()).isEqualTo("ds1");
    }

    @Test
    @DisplayName("resolveForEngine returns non-secret config with credentialRef")
    void resolveForEngine_returnsConfigWithCredentialRef() {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId("acme");
        e.setRef("ds1");
        e.setJdbcUrl("jdbc:h2:mem:t");
        e.setDriverClassName("org.h2.Driver");
        e.setCredentialRef("my-cred");
        e.setDialect("h2");
        e.setPoolMinSize(1);
        e.setPoolMaxSize(5);
        e.setConnectionTimeoutSeconds(5);
        e.setIdleTimeoutSeconds(600);
        when(repository.findByTenantIdAndRef("acme", "ds1")).thenReturn(Optional.of(e));

        DatasourceEngineConfig cfg = service.resolveForEngine("acme", "ds1");

        assertThat(cfg.credentialRef()).isEqualTo("my-cred");
        assertThat(cfg.jdbcUrl()).isEqualTo("jdbc:h2:mem:t");
        assertThat(cfg.driverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    @DisplayName("testConnection returns a safe error when the credential cannot be resolved")
    void testConnection_credentialMissing_returnsSafeError() {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId("acme");
        e.setRef("ds1");
        e.setCredentialRef("missing-cred");
        e.setJdbcUrl("jdbc:h2:mem:t");
        e.setDriverClassName("org.h2.Driver");
        when(repository.findByTenantIdAndRef("acme", "ds1")).thenReturn(Optional.of(e));
        when(credentialService.resolvePath("acme", "jdbc-password", "missing-cred"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));

        DatasourceTestResult result = service.testConnection("acme", "ds1");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("missing-cred");
    }

    @Test
    @DisplayName("testConnection returns a safe error when OpenBao read throws (no 500)")
    void testConnection_vaultUnavailable_returnsSafeError() {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId("acme");
        e.setRef("ds1");
        e.setCredentialRef("my-cred");
        e.setJdbcUrl("jdbc:h2:mem:t");
        e.setDriverClassName("org.h2.Driver");
        when(repository.findByTenantIdAndRef("acme", "ds1")).thenReturn(Optional.of(e));
        when(credentialService.resolvePath("acme", "jdbc-password", "my-cred"))
            .thenReturn(new com.werkflow.admin.dto.credential.CredentialPathResponse(
                "acme", "jdbc-password", "my-cred", "tenants/acme/jdbc-password/my-cred"));
        when(vault.read("tenants/acme/jdbc-password/my-cred"))
            .thenThrow(new VaultException("OpenBao unreachable"));

        DatasourceTestResult result = service.testConnection("acme", "ds1");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("resolution failed");
    }

    @Test
    @DisplayName("testConnection returns a safe error when the credential lacks username/password")
    void testConnection_missingFields_returnsSafeError() {
        TenantDatasource e = new TenantDatasource();
        e.setTenantId("acme");
        e.setRef("ds1");
        e.setCredentialRef("my-cred");
        e.setJdbcUrl("jdbc:h2:mem:t");
        e.setDriverClassName("org.h2.Driver");
        when(repository.findByTenantIdAndRef("acme", "ds1")).thenReturn(Optional.of(e));
        when(credentialService.resolvePath("acme", "jdbc-password", "my-cred"))
            .thenReturn(new com.werkflow.admin.dto.credential.CredentialPathResponse(
                "acme", "jdbc-password", "my-cred", "tenants/acme/jdbc-password/my-cred"));
        when(vault.read("tenants/acme/jdbc-password/my-cred"))
            .thenReturn(Optional.of(Map.of("username", "sa")));  // password missing

        DatasourceTestResult result = service.testConnection("acme", "ds1");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("missing username or password");
    }
}
