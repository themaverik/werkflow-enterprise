package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.*;
import com.werkflow.admin.entity.TenantApiCredential;
import com.werkflow.admin.entity.TenantServiceEndpoint;
import com.werkflow.admin.repository.TenantApiCredentialRepository;
import com.werkflow.admin.repository.TenantServiceEndpointRepository;
import com.werkflow.common.security.EncryptionService;
import com.werkflow.common.security.SsrfGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorService {

    private static final int MAX_RESPONSE_BYTES = 100 * 1024;

    @Value("${app.engine-service.url}")
    private String engineServiceUrl;

    @Value("${app.erp-service.url}")
    private String erpServiceUrl;

    private RestClient engineRestClient;
    private RestClient erpRestClient;

    private final TenantApiCredentialRepository credentialRepo;
    private final TenantServiceEndpointRepository endpointRepo;
    private final EncryptionService encryptionService;
    private final SsrfGuard ssrfGuard;

    @PostConstruct
    private void initRestClients() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        this.engineRestClient = RestClient.builder().requestFactory(factory).build();
        this.erpRestClient = RestClient.builder().requestFactory(factory).build();
    }

    @Transactional(readOnly = true)
    public List<ConnectorResponse> listByTenant(String tenantCode) {
        return endpointRepo.findByTenantCodeAndActiveTrue(tenantCode).stream()
            .flatMap(ep -> {
                Optional<TenantApiCredential> cred =
                    credentialRepo.findByTenantCodeAndConnectorKey(tenantCode, ep.getConnectorKey());
                if (cred.isEmpty()) {
                    log.warn("No credential found for connector '{}' tenant '{}' — skipping",
                             ep.getConnectorKey(), tenantCode);
                    return Stream.<ConnectorResponse>empty();
                }
                return Stream.of(toResponse(ep, cred.get()));
            })
            .toList();
    }

    @Transactional
    public ConnectorResponse create(ConnectorRequest request) {
        TenantServiceEndpoint ep = new TenantServiceEndpoint();
        ep.setTenantCode(request.getTenantCode());
        ep.setConnectorKey(request.getConnectorKey());
        ep.setServiceKey(request.getConnectorKey());
        ep.setDisplayName(request.getDisplayName());
        ep.setBaseUrl(request.getBaseUrl());
        ep.setEnvironment(request.getEnvironment());
        ep.setActive(request.isActive());
        ep.setSampleSchema(request.getSampleSchema());
        ep.setConnectorType(request.getConnectorType() != null ? request.getConnectorType() : "API");
        endpointRepo.save(ep);

        TenantApiCredential cred = new TenantApiCredential();
        cred.setTenantCode(request.getTenantCode());
        cred.setConnectorKey(request.getConnectorKey());
        cred.setCredentialKey(request.getConnectorKey());
        cred.setLabel(request.getDisplayName());
        cred.setAuthScheme(request.getAuthScheme());
        cred.setHeaderName(request.getHeaderName());
        if (request.getSecretValue() != null && !request.getSecretValue().isBlank()) {
            cred.setSecretValue(encryptionService.encrypt(request.getSecretValue()));
        }
        credentialRepo.save(cred);

        return toResponse(ep, cred);
    }

    @Transactional
    public ConnectorResponse update(String tenantCode, String connectorKey, ConnectorUpdateRequest request) {
        TenantServiceEndpoint ep = endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey));
        ep.setDisplayName(request.getDisplayName());
        ep.setBaseUrl(request.getBaseUrl());
        ep.setEnvironment(request.getEnvironment());
        ep.setActive(request.isActive());
        if (request.getConnectorType() != null) ep.setConnectorType(request.getConnectorType());
        endpointRepo.save(ep);

        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        cred.setLabel(request.getDisplayName());
        cred.setAuthScheme(request.getAuthScheme());
        cred.setHeaderName(request.getHeaderName());
        if (request.getSecretValue() != null && !request.getSecretValue().isBlank()) {
            cred.setSecretValue(encryptionService.encrypt(request.getSecretValue()));
        }
        credentialRepo.save(cred);

        return toResponse(ep, cred);
    }

    @Transactional
    public ConnectorResponse updateSampleSchema(String tenantCode, String connectorKey, String sampleSchema) {
        TenantServiceEndpoint ep = endpointRepo
            .findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, "production")
            .orElseGet(() -> endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey)));
        ep.setSampleSchema(sampleSchema);
        endpointRepo.save(ep);
        ConnectorResponse response = toResponse(ep, findCredential(tenantCode, connectorKey));
        CompletableFuture.runAsync(() -> notifyEngineCache(tenantCode, connectorKey));
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<String> getSchema(String tenantCode, String connectorKey) {
        return endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .stream().findFirst()
            .map(TenantServiceEndpoint::getSampleSchema);
    }

    public ConnectorTestResponse testCall(String tenantCode, String connectorKey, ConnectorTestRequest request) {
        TenantServiceEndpoint ep = endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey));
        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        ConnectorTestResponse result = executeProxy(ep, cred, request.getPath(), request.getMethod(), request.getRequestBody());
        log.info("connector.test.call tenantCode={} connectorKey={} path={} method={} status={} durationMs={} truncated={}",
            tenantCode, connectorKey, request.getPath(), request.getMethod(),
            result.getStatusCode(), result.getDurationMs(), result.isTruncated());
        return result;
    }

    /**
     * Outbound proxy used by the portal server-side route and engine ConnectorCallDelegate.
     * Decrypts stored credential, enforces SSRF guard, and forwards the call to the external connector.
     */
    public ConnectorTestResponse callConnector(String tenantCode, String connectorKey,
                                               String path, String method, String requestBody) {
        TenantServiceEndpoint ep = endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey));
        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        ConnectorTestResponse result = executeProxy(ep, cred, path, method, requestBody);
        log.info("connector.call tenantCode={} connectorKey={} path={} method={} status={} durationMs={}",
            tenantCode, connectorKey, path, method, result.getStatusCode(), result.getDurationMs());
        return result;
    }

    /**
     * Registers a pre-hashed API key in ERP and stores the encrypted raw key for outbound calls.
     * Validates that SHA-256(rawKey) == keyHash before calling ERP (defense in depth).
     * The user's JWT is forwarded to ERP so tenantId is extracted from the token there.
     */
    @Transactional
    public void registerApiKey(String tenantCode, String connectorKey,
                               ConnectorApiKeyRequest request, String bearerToken) {
        String expectedHash = sha256Hex(request.getRawKey());
        if (!expectedHash.equalsIgnoreCase(request.getKeyHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyHash does not match SHA-256 of rawKey");
        }

        Map<String, String> erpPayload = Map.of(
            "keyHash", request.getKeyHash(),
            "name", request.getKeyName()
        );
        try {
            erpRestClient.post()
                .uri(erpServiceUrl + "/api/v1/api-keys")
                .header("Authorization", bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(erpPayload)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to register API key in ERP for connector '{}': {}", connectorKey, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ERP api-key registration failed: " + e.getMessage());
        }

        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        cred.setSecretValue(encryptionService.encrypt(request.getRawKey()));
        cred.setAuthScheme("API_KEY");
        credentialRepo.save(cred);

        log.info("connector.api-key.registered tenantCode={} connectorKey={}", tenantCode, connectorKey);
    }

    private ConnectorTestResponse executeProxy(TenantServiceEndpoint ep, TenantApiCredential cred,
                                               String path, String method, String requestBody) {
        String fullUrl = ep.getBaseUrl() + path;
        ssrfGuard.validateExternal(fullUrl);

        String rawSecret = cred.getSecretValue() != null
            ? encryptionService.decrypt(cred.getSecretValue())
            : null;

        String authHeader = cred.getHeaderName() != null ? cred.getHeaderName() : "Authorization";
        String authValue = switch (cred.getAuthScheme()) {
            case "NONE" -> null;
            case "BEARER" -> rawSecret != null ? "Bearer " + rawSecret : null;
            case "API_KEY" -> rawSecret;
            case "BASIC" -> rawSecret != null ? "Basic " + rawSecret : null;
            default -> rawSecret;
        };

        RestClient proxyClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();

        long start = Instant.now().toEpochMilli();
        final int[] statusHolder = {0};
        final Map<String, String> responseHeaders = new LinkedHashMap<>();

        var spec = proxyClient.method(HttpMethod.valueOf(method))
            .uri(fullUrl)
            .headers(headers -> {
                if (authValue != null) headers.set(authHeader, authValue);
            });
        if (requestBody != null && !requestBody.isBlank()) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(requestBody);
        }
        var entity = spec.retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
            .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {})
            .toEntity(byte[].class);

        statusHolder[0] = entity.getStatusCode().value();
        entity.getHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (!lower.equals("set-cookie") && !lower.equals("www-authenticate")) {
                responseHeaders.put(name, String.join(", ", values));
            }
        });
        byte[] raw = entity.getBody() != null ? entity.getBody() : new byte[0];
        boolean truncated = raw.length > MAX_RESPONSE_BYTES;
        String body = new String(truncated ? Arrays.copyOf(raw, MAX_RESPONSE_BYTES) : raw, StandardCharsets.UTF_8);

        return ConnectorTestResponse.builder()
            .statusCode(statusHolder[0])
            .headers(responseHeaders)
            .body(body)
            .truncated(truncated)
            .durationMs(Instant.now().toEpochMilli() - start)
            .build();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveBaseUrl(String tenantCode, String connectorKey, String environment) {
        return endpointRepo
            .findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, environment)
            .filter(TenantServiceEndpoint::isActive)
            .map(TenantServiceEndpoint::getBaseUrl);
    }

    private void notifyEngineCache(String tenantCode, String connectorKey) {
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(engineServiceUrl)
                .path("/internal/cache/endpoints/invalidate")
                .queryParam("tenantCode", tenantCode)
                .queryParam("connectorKey", connectorKey)
                .toUriString();
            engineRestClient.post().uri(uri).retrieve().toBodilessEntity();
            log.debug("Notified engine to invalidate cache for {}/{}", tenantCode, connectorKey);
        } catch (Exception e) {
            log.warn("Failed to notify engine cache invalidation for {}/{}: {}",
                     tenantCode, connectorKey, e.getMessage());
        }
    }

    private TenantApiCredential findCredential(String tenantCode, String connectorKey) {
        return credentialRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .orElseThrow(() -> new NoSuchElementException("Credential not found for connector: " + connectorKey));
    }

    private ConnectorResponse toResponse(TenantServiceEndpoint ep, TenantApiCredential cred) {
        return ConnectorResponse.builder()
            .endpointId(ep.getId())
            .credentialId(cred.getId())
            .tenantCode(ep.getTenantCode())
            .connectorKey(ep.getConnectorKey())
            .displayName(ep.getDisplayName())
            .baseUrl(ep.getBaseUrl())
            .environment(ep.getEnvironment())
            .active(ep.isActive())
            .connectorType(ep.getConnectorType())
            .authScheme(cred.getAuthScheme())
            .headerName(cred.getHeaderName())
            .sampleSchema(ep.getSampleSchema())
            .hasSecret(cred.getSecretValue() != null && !cred.getSecretValue().isBlank())
            .createdAt(ep.getCreatedAt())
            .updatedAt(ep.getUpdatedAt())
            .build();
    }
}
