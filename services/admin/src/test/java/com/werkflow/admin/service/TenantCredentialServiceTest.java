package com.werkflow.admin.service;

import com.werkflow.admin.dto.credential.CreateTenantCredentialRequest;
import com.werkflow.admin.dto.credential.CredentialPathResponse;
import com.werkflow.admin.dto.credential.CredentialTestResultResponse;
import com.werkflow.admin.dto.credential.TenantCredentialResponse;
import com.werkflow.admin.dto.credential.UpdateTenantCredentialRequest;
import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.entity.TenantDatasource;
import com.werkflow.admin.event.DatasourcePoolEvictionEvent;
import com.werkflow.admin.repository.TenantCredentialRepository;
import com.werkflow.admin.repository.TenantDatasourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCredentialServiceTest {

    @Mock TenantCredentialRepository repository;
    @Mock VaultCredentialStore vault;
    @Mock CredentialTestClient credentialTestClient;
    @Mock TenantDatasourceRepository datasourceRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks TenantCredentialService service;

    private static final String TENANT = "tenant-1";
    private static final String OTHER_TENANT = "tenant-2";

    private TenantCredential entity(UUID id, String tenant, String type, String label) {
        TenantCredential e = new TenantCredential(tenant, type, label,
            "tenants/" + tenant + "/" + type + "/" + label);
        e.setId(id);
        return e;
    }

    // -- get / list ---------------------------------------------------------

    @Test
    @DisplayName("get returns 404 when entity belongs to another tenant (OWASP BOLA)")
    void get_crossTenant_throws404() {
        UUID id = UUID.randomUUID();
        TenantCredential other = entity(id, OTHER_TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.get(TENANT, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("get returns 404 when id does not exist")
    void get_missing_throws404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TENANT, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("list returns empty fieldNames since plaintext is never echoed")
    void list_returnsMetadataOnly() {
        TenantCredential e = entity(UUID.randomUUID(), TENANT, "smtp", "default");
        when(repository.findByTenantId(TENANT)).thenReturn(List.of(e));

        List<TenantCredentialResponse> result = service.list(TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fieldNames()).isEmpty();
    }

    // -- create + compensation ---------------------------------------------

    @Test
    @DisplayName("create writes Vault then DB; returns metadata with field names")
    void create_happyPath() {
        CreateTenantCredentialRequest req = new CreateTenantCredentialRequest(
            "smtp", "default", Map.of("host", "smtp.example.com", "password", "s3cr3t"));
        when(repository.existsByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(false);
        when(repository.save(any(TenantCredential.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        TenantCredentialResponse response = service.create(TENANT, req);

        assertThat(response.credentialType()).isEqualTo("smtp");
        assertThat(response.fieldNames()).containsExactlyInAnyOrder("host", "password");
        // Vault write happens before DB save
        verify(vault).write(eq("tenants/tenant-1/smtp/default"), anyMap());
        verify(repository).save(any(TenantCredential.class));
    }

    @Test
    @DisplayName("create rolls back Vault write when DB insert fails")
    void create_dbFailure_compensatesVault() {
        CreateTenantCredentialRequest req = new CreateTenantCredentialRequest(
            "smtp", "default", Map.of("host", "smtp.example.com"));
        when(repository.existsByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(false);
        when(repository.save(any(TenantCredential.class)))
            .thenThrow(new RuntimeException("constraint violation"));

        assertThatThrownBy(() -> service.create(TENANT, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("constraint violation");

        verify(vault).write(eq("tenants/tenant-1/smtp/default"), anyMap());
        verify(vault).delete("tenants/tenant-1/smtp/default");
    }

    @Test
    @DisplayName("create rolls back compensation that itself fails logs warning but still surfaces DB error")
    void create_dbFailureAndCompensationFailure_surfacesDbError() {
        CreateTenantCredentialRequest req = new CreateTenantCredentialRequest(
            "smtp", "default", Map.of("host", "smtp.example.com"));
        when(repository.existsByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(false);
        when(repository.save(any(TenantCredential.class)))
            .thenThrow(new RuntimeException("db down"));
        doThrow(new VaultException("vault unreachable"))
            .when(vault).delete("tenants/tenant-1/smtp/default");

        assertThatThrownBy(() -> service.create(TENANT, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("db down");
    }

    @Test
    @DisplayName("create returns 409 on duplicate (tenantId, type, label)")
    void create_duplicate_throws409() {
        CreateTenantCredentialRequest req = new CreateTenantCredentialRequest(
            "smtp", "default", Map.of("host", "smtp.example.com"));
        when(repository.existsByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(TENANT, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verify(vault, never()).write(any(), anyMap());
    }

    // -- update ------------------------------------------------------------

    @Test
    @DisplayName("update writes Vault new version, sets rotatedAt, returns response with field names")
    void update_happyPath() {
        UUID id = UUID.randomUUID();
        TenantCredential e = entity(id, TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(repository.save(any(TenantCredential.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        TenantCredentialResponse response = service.update(TENANT, id,
            new UpdateTenantCredentialRequest(Map.of("password", "new-pass")));

        assertThat(response.fieldNames()).containsExactly("password");
        assertThat(e.getRotatedAt()).isNotNull();
        verify(vault).write(eq("tenants/tenant-1/smtp/default"), anyMap());
    }

    @Test
    @DisplayName("update returns 404 when entity belongs to another tenant")
    void update_crossTenant_throws404() {
        UUID id = UUID.randomUUID();
        TenantCredential other = entity(id, OTHER_TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(TENANT, id,
            new UpdateTenantCredentialRequest(Map.of("password", "x"))))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(vault, never()).write(any(), anyMap());
    }

    @Test
    @DisplayName("rotating a jdbc-password credential publishes an eviction event per referencing datasource")
    void update_jdbcPassword_publishesEvictionPerDatasource() {
        UUID id = UUID.randomUUID();
        TenantCredential e = entity(id, TENANT, "jdbc-password", "hris-db");
        when(repository.findById(id)).thenReturn(Optional.of(e));
        when(repository.save(any(TenantCredential.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantDatasource ds1 = new TenantDatasource();
        ds1.setTenantId(TENANT);
        ds1.setRef("ds-a");
        TenantDatasource ds2 = new TenantDatasource();
        ds2.setTenantId(TENANT);
        ds2.setRef("ds-b");
        when(datasourceRepository.findByTenantIdAndCredentialRef(TENANT, "hris-db"))
            .thenReturn(List.of(ds1, ds2));

        service.update(TENANT, id,
            new UpdateTenantCredentialRequest(Map.of("username", "u", "password", "p")));

        verify(eventPublisher).publishEvent(new DatasourcePoolEvictionEvent(TENANT, "ds-a"));
        verify(eventPublisher).publishEvent(new DatasourcePoolEvictionEvent(TENANT, "ds-b"));
    }

    // -- delete ------------------------------------------------------------

    @Test
    @DisplayName("delete removes DB row first, then Vault soft-deletes")
    void delete_happyPath() {
        UUID id = UUID.randomUUID();
        TenantCredential e = entity(id, TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(e));

        service.delete(TENANT, id);

        verify(repository).delete(e);
        verify(vault).delete("tenants/tenant-1/smtp/default");
    }

    @Test
    @DisplayName("delete still completes (warning only) when Vault soft-delete fails after DB delete")
    void delete_vaultFailure_doesNotRollbackDb() {
        UUID id = UUID.randomUUID();
        TenantCredential e = entity(id, TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(e));
        doThrow(new VaultException("vault unreachable"))
            .when(vault).delete("tenants/tenant-1/smtp/default");

        // Should not throw — DB row is gone, orphan in Vault is unreachable through metadata lookup
        service.delete(TENANT, id);
        verify(repository).delete(e);
    }

    @Test
    @DisplayName("delete returns 404 on cross-tenant id")
    void delete_crossTenant_throws404() {
        UUID id = UUID.randomUUID();
        TenantCredential other = entity(id, OTHER_TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.delete(TENANT, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(repository, never()).delete(any(TenantCredential.class));
        verify(vault, never()).delete(any());
    }

    // -- testConnection ----------------------------------------------------

    @Test
    @DisplayName("testConnection delegates to engine via CredentialTestClient and returns its outcome")
    void testConnection_happyPath() {
        UUID id = UUID.randomUUID();
        TenantCredential e = entity(id, TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(e));
        CredentialTestResultResponse expected = new CredentialTestResultResponse(true, "OK");
        when(credentialTestClient.test(TENANT, "smtp", "default")).thenReturn(expected);

        CredentialTestResultResponse result = service.testConnection(TENANT, id);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("testConnection returns 404 on cross-tenant id; never calls engine")
    void testConnection_crossTenant_throws404() {
        UUID id = UUID.randomUUID();
        TenantCredential other = entity(id, OTHER_TENANT, "smtp", "default");
        when(repository.findById(id)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.testConnection(TENANT, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(credentialTestClient, never()).test(any(), any(), any());
    }

    // -- resolvePath (engine-internal) -------------------------------------

    @Test
    @DisplayName("resolvePath returns Vault path on hit")
    void resolvePath_happyPath() {
        TenantCredential e = entity(UUID.randomUUID(), TENANT, "smtp", "default");
        when(repository.findByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(Optional.of(e));

        CredentialPathResponse response = service.resolvePath(TENANT, "smtp", "default");

        assertThat(response.vaultPath()).isEqualTo("tenants/tenant-1/smtp/default");
        assertThat(response.tenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("resolvePath throws 404 when triple has no metadata row")
    void resolvePath_missing_throws404() {
        when(repository.findByTenantIdAndCredentialTypeAndLabel(TENANT, "smtp", "default"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolvePath(TENANT, "smtp", "default"))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // -- Vault payload content verification ---------------------------------

    @Test
    @DisplayName("create writes exactly the supplied values to Vault (no leakage, no extra keys)")
    void create_writesExactPayloadToVault() {
        Map<String, Object> values = Map.of("botToken", "xoxb-abc", "signingSecret", "shh");
        CreateTenantCredentialRequest req = new CreateTenantCredentialRequest(
            "slack-bot-token", "alpha", values);
        when(repository.existsByTenantIdAndCredentialTypeAndLabel(TENANT, "slack-bot-token", "alpha"))
            .thenReturn(false);
        when(repository.save(any(TenantCredential.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.create(TENANT, req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vault).write(eq("tenants/tenant-1/slack-bot-token/alpha"), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("botToken", "signingSecret");
        assertThat(captor.getValue().get("botToken")).isEqualTo("xoxb-abc");
    }
}
