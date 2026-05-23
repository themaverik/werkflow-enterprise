package com.werkflow.admin.service;

import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.repository.TenantCredentialRepository;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCredentialServiceDeleteGuardTest {

    @Mock private TenantCredentialRepository repository;
    @Mock private VaultCredentialStore vault;
    @Mock private CredentialTestClient credentialTestClient;
    @Mock private TenantDatasourceRepository datasourceRepository;

    private TenantCredentialService service;

    @BeforeEach
    void setUp() {
        service = new TenantCredentialService(repository, vault, credentialTestClient, datasourceRepository);
    }

    private TenantCredential jdbcCredential() {
        return new TenantCredential("acme", "jdbc-password", "my-cred", "tenants/acme/jdbc-password/my-cred");
    }

    @Test
    @DisplayName("delete is blocked with 409 when a datasource references the jdbc credential")
    void delete_blockedWhenDatasourceReferencesCredential() {
        UUID id = UUID.randomUUID();
        TenantCredential cred = jdbcCredential();
        when(repository.findById(id)).thenReturn(Optional.of(cred));
        TenantDatasource ds = new TenantDatasource();
        ds.setRef("ds1");
        when(datasourceRepository.findByTenantIdAndCredentialRef("acme", "my-cred"))
            .thenReturn(List.of(ds));

        assertThatThrownBy(() -> service.delete("acme", id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ds1");

        verify(repository, never()).delete(cred);
    }

    @Test
    @DisplayName("delete proceeds when no datasource references the credential")
    void delete_proceedsWhenNoDependents() {
        UUID id = UUID.randomUUID();
        TenantCredential cred = jdbcCredential();
        when(repository.findById(id)).thenReturn(Optional.of(cred));
        when(datasourceRepository.findByTenantIdAndCredentialRef("acme", "my-cred"))
            .thenReturn(List.of());

        service.delete("acme", id);

        verify(repository).delete(cred);
        verify(vault).delete("tenants/acme/jdbc-password/my-cred");
    }
}
