package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.ConnectorApiKeyRequest;
import com.werkflow.admin.entity.TenantApiCredential;
import com.werkflow.admin.entity.TenantServiceEndpoint;
import com.werkflow.admin.repository.TenantApiCredentialRepository;
import com.werkflow.admin.repository.TenantCredentialRepository;
import com.werkflow.admin.repository.TenantServiceEndpointRepository;
import com.werkflow.common.security.SsrfGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock TenantApiCredentialRepository credentialRepo;
    @Mock TenantServiceEndpointRepository endpointRepo;
    @Mock TenantCredentialRepository tenantCredentialRepo;
    @Mock VaultCredentialStore vault;
    @Mock SsrfGuard ssrfGuard;

    @InjectMocks ConnectorService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "engineServiceUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "erpServiceUrl", "http://localhost:8084");
    }

    // --- registerApiKey ---

    @Test
    void registerApiKey_rejectsHashMismatch() {
        ConnectorApiKeyRequest req = new ConnectorApiKeyRequest();
        req.setRawKey("my-raw-key");
        req.setKeyHash("0000000000000000000000000000000000000000000000000000000000000000");
        req.setKeyName("My Key");

        assertThatThrownBy(() -> service.registerApiKey("acme", "erp-connector", req, "Bearer token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("keyHash does not match");
    }

    @Test
    void registerApiKey_hashMismatchDetectedBeforeAnyExternalCall() {
        ConnectorApiKeyRequest req = new ConnectorApiKeyRequest();
        req.setRawKey("rawKey");
        req.setKeyHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        req.setKeyName("test");

        assertThatThrownBy(() -> service.registerApiKey("acme", "erp-connector", req, "Bearer x"))
            .isInstanceOf(ResponseStatusException.class);

        // Verify no DB, vault, or ERP calls were made
        verifyNoInteractions(endpointRepo, credentialRepo, vault, tenantCredentialRepo);
    }

    // --- callConnector ---

    @Test
    void callConnector_throwsWhenConnectorNotFound() {
        when(endpointRepo.findByTenantCodeAndConnectorKey("acme", "missing-connector"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.callConnector("acme", "missing-connector", "/data", "GET", null))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Connector not found");
    }

    @Test
    void callConnector_throwsWhenCredentialNotFound() {
        TenantServiceEndpoint ep = endpoint("acme", "erp-connector", "http://external-api.example.com");
        when(endpointRepo.findByTenantCodeAndConnectorKey("acme", "erp-connector"))
            .thenReturn(List.of(ep));
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "erp-connector"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.callConnector("acme", "erp-connector", "/data", "GET", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Credential not found");
    }

    // --- resolveCredentialBinding (ADR-024) ---

    @Test
    void resolveCredentialBinding_bearer_mapsToHttpBearerTokenSlug() {
        TenantApiCredential cred = credential("acme", "crm-api", "BEARER");
        cred.setCredentialRef("prod-token");
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "crm-api"))
            .thenReturn(Optional.of(cred));

        var binding = service.resolveCredentialBinding("acme", "crm-api");

        assertThat(binding).hasValueSatisfying(b -> {
            assertThat(b.credentialType()).isEqualTo("http-bearer-token");
            assertThat(b.credentialRef()).isEqualTo("prod-token");
        });
    }

    @Test
    void resolveCredentialBinding_basic_mapsToHttpBasicAuthSlug() {
        TenantApiCredential cred = credential("acme", "billing-api", "BASIC");
        cred.setCredentialRef("svc-account");
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "billing-api"))
            .thenReturn(Optional.of(cred));

        assertThat(service.resolveCredentialBinding("acme", "billing-api"))
            .hasValueSatisfying(b -> assertThat(b.credentialType()).isEqualTo("http-basic-auth"));
    }

    @Test
    void resolveCredentialBinding_apiKey_mapsToHttpHeaderAuthSlug() {
        TenantApiCredential cred = credential("acme", "erp-connector", "API_KEY");
        cred.setCredentialRef("erp-key");
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "erp-connector"))
            .thenReturn(Optional.of(cred));

        assertThat(service.resolveCredentialBinding("acme", "erp-connector"))
            .hasValueSatisfying(b -> assertThat(b.credentialType()).isEqualTo("http-header-auth"));
    }

    @Test
    void resolveCredentialBinding_authSchemeNone_returnsEmpty() {
        TenantApiCredential cred = credential("acme", "public-api", "NONE");
        cred.setCredentialRef("ignored");
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "public-api"))
            .thenReturn(Optional.of(cred));

        assertThat(service.resolveCredentialBinding("acme", "public-api")).isEmpty();
    }

    @Test
    void resolveCredentialBinding_noBoundCredentialRef_returnsEmpty() {
        TenantApiCredential cred = credential("acme", "crm-api", "BEARER"); // credentialRef left null
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "crm-api"))
            .thenReturn(Optional.of(cred));

        assertThat(service.resolveCredentialBinding("acme", "crm-api")).isEmpty();
    }

    @Test
    void resolveCredentialBinding_unregisteredConnector_returnsEmpty() {
        when(credentialRepo.findByTenantCodeAndConnectorKey("acme", "missing"))
            .thenReturn(Optional.empty());

        assertThat(service.resolveCredentialBinding("acme", "missing")).isEmpty();
    }

    // --- helpers ---

    private TenantServiceEndpoint endpoint(String tenant, String key, String baseUrl) {
        TenantServiceEndpoint ep = new TenantServiceEndpoint();
        ep.setTenantCode(tenant);
        ep.setConnectorKey(key);
        ep.setBaseUrl(baseUrl);
        ep.setActive(true);
        return ep;
    }

    private TenantApiCredential credential(String tenant, String key, String scheme) {
        TenantApiCredential cred = new TenantApiCredential();
        cred.setTenantCode(tenant);
        cred.setConnectorKey(key);
        cred.setAuthScheme(scheme);
        return cred;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
