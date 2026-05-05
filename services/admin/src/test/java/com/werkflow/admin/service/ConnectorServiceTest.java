package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.ConnectorApiKeyRequest;
import com.werkflow.admin.entity.TenantApiCredential;
import com.werkflow.admin.entity.TenantServiceEndpoint;
import com.werkflow.admin.repository.TenantApiCredentialRepository;
import com.werkflow.admin.repository.TenantServiceEndpointRepository;
import com.werkflow.common.security.EncryptionService;
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
    @Mock EncryptionService encryptionService;
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

        // Verify no DB or ERP calls were made
        verifyNoInteractions(endpointRepo, credentialRepo, encryptionService);
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
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Credential not found");
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
