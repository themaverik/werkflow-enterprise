package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.*;
import com.werkflow.admin.entity.TenantApiCredential;
import com.werkflow.admin.entity.TenantCredential;
import com.werkflow.admin.entity.TenantServiceEndpoint;
import com.werkflow.admin.repository.TenantApiCredentialRepository;
import com.werkflow.admin.repository.TenantCredentialRepository;
import com.werkflow.admin.repository.TenantServiceEndpointRepository;
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
import java.time.OffsetDateTime;
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
    private final TenantCredentialRepository tenantCredentialRepo;
    private final VaultCredentialStore vault;
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
        cred.setCredentialRef(blankToNull(request.getCredentialRef()));
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
        if (request.getCredentialRef() != null && !request.getCredentialRef().isBlank()) {
            cred.setCredentialRef(request.getCredentialRef());
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

    @Transactional
    public ConnectorResponse updateEndpointSchema(String tenantCode, Long endpointId, String sampleSchema) {
        TenantServiceEndpoint ep = endpointRepo.findById(endpointId)
            .orElseThrow(() -> new NoSuchElementException("Endpoint not found: " + endpointId));
        if (!ep.getTenantCode().equals(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Endpoint does not belong to tenant");
        }
        ep.setSampleSchema(sampleSchema);
        endpointRepo.save(ep);
        TenantApiCredential cred = findCredential(tenantCode, ep.getConnectorKey());
        CompletableFuture.runAsync(() -> notifyEngineCache(tenantCode, ep.getConnectorKey()));
        return toResponse(ep, cred);
    }

    @Transactional(readOnly = true)
    public Optional<String> getSchema(String tenantCode, String connectorKey) {
        return endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .stream().findFirst()
            .map(TenantServiceEndpoint::getSampleSchema);
    }

    @Transactional
    public void delete(String tenantCode, String connectorKey) {
        endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .forEach(endpointRepo::delete);
        credentialRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .ifPresent(credentialRepo::delete);
        log.info("connector.deleted tenantCode={} connectorKey={}", tenantCode, connectorKey);
    }

    @Transactional
    public ConnectorResponse addEndpoint(String tenantCode, String connectorKey, ConnectorEndpointRequest request) {
        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        if (endpointRepo.findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, request.getEnvironment()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Endpoint for environment '" + request.getEnvironment() + "' already exists");
        }
        TenantServiceEndpoint ep = new TenantServiceEndpoint();
        ep.setTenantCode(tenantCode);
        ep.setConnectorKey(connectorKey);
        ep.setServiceKey(connectorKey);
        ep.setDisplayName(cred.getLabel());
        ep.setBaseUrl(request.getBaseUrl());
        ep.setEnvironment(request.getEnvironment());
        ep.setActive(request.isActive());
        ep.setConnectorType(request.getConnectorType() != null ? request.getConnectorType() : "API");
        endpointRepo.save(ep);
        log.info("connector.endpoint.added tenantCode={} connectorKey={} environment={}", tenantCode, connectorKey, request.getEnvironment());
        return toResponse(ep, cred);
    }

    @Transactional
    public void deleteEndpoint(String tenantCode, Long endpointId) {
        TenantServiceEndpoint ep = endpointRepo.findById(endpointId)
            .orElseThrow(() -> new NoSuchElementException("Endpoint not found: " + endpointId));
        if (!ep.getTenantCode().equals(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Endpoint does not belong to tenant");
        }
        List<TenantServiceEndpoint> all = endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, ep.getConnectorKey());
        if (all.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot delete the last endpoint — delete the connector instead");
        }
        endpointRepo.delete(ep);
        log.info("connector.endpoint.deleted tenantCode={} endpointId={}", tenantCode, endpointId);
    }

    public ConnectorTestResponse testCall(String tenantCode, String connectorKey, ConnectorTestRequest request) {
        TenantServiceEndpoint ep = request.getEnvironment() != null
            ? endpointRepo.findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, request.getEnvironment())
                .orElseThrow(() -> new NoSuchElementException("No " + request.getEnvironment() + " endpoint for: " + connectorKey))
            : endpointRepo.findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, "production")
                .orElseGet(() -> endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
                    .stream().findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey)));
        TenantApiCredential cred = findCredential(tenantCode, connectorKey);
        ConnectorTestResponse result = executeProxy(ep, cred, request.getPath(), request.getMethod(), request.getRequestBody());
        log.info("connector.test.call tenantCode={} connectorKey={} path={} method={} status={} durationMs={} truncated={}",
            tenantCode, connectorKey, request.getPath(), request.getMethod(),
            result.getStatusCode(), result.getDurationMs(), result.isTruncated());
        return result;
    }

    /**
     * Outbound proxy used by the portal server-side route and the engine connector delegates.
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
     * Registers a pre-hashed API key in ERP and stores the raw key in OpenBao as an
     * {@code http-header-auth} credential for outbound calls (Phase B.6 — replaces the
     * legacy AES {@code secretValue} path). Validates that SHA-256(rawKey) == keyHash before
     * calling ERP (defense in depth). The user's JWT is forwarded to ERP so tenantId is
     * extracted from the token there.
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
            "name", request.getKeyName(),
            "tenantId", tenantCode
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
        String credentialType = "http-header-auth";
        // connectorKey regex permits '_'; the credential label regex (^[a-z][a-z0-9-]*$) does not,
        // so map '_'→'-'. connectorKey is letter-led, so the result always satisfies the label CHECK.
        String label = connectorKey.replace('_', '-');
        String vaultPath = "tenants/" + tenantCode + "/" + credentialType + "/" + label;
        // ERP API keys are presented in the Authorization header (the pre-B.6 default; the
        // connector no longer carries a configurable header name — that lives on the credential).
        Map<String, Object> values = Map.of("headerName", "Authorization", "headerValue", request.getRawKey());

        // Vault write first; compensate (soft-delete) if the DB updates below fail, mirroring
        // TenantCredentialService.create — otherwise a transaction rollback leaves an orphan
        // Vault version with no metadata row pointing at it.
        vault.write(vaultPath, values);
        try {
            TenantCredential meta = tenantCredentialRepo
                .findByTenantIdAndCredentialTypeAndLabel(tenantCode, credentialType, label)
                .orElseGet(() -> new TenantCredential(tenantCode, credentialType, label, vaultPath));
            meta.setRotatedAt(OffsetDateTime.now());
            tenantCredentialRepo.save(meta);

            cred.setAuthScheme("API_KEY");
            cred.setCredentialRef(label);
            credentialRepo.save(cred);
        } catch (RuntimeException dbEx) {
            try {
                vault.delete(vaultPath);
            } catch (RuntimeException compensateEx) {
                log.error("Vault compensation delete failed; manual cleanup required for path={}", vaultPath, compensateEx);
            }
            throw dbEx;
        }

        log.info("connector.api-key.registered tenantCode={} connectorKey={} credentialRef={}",
            tenantCode, connectorKey, label);
    }

    private ConnectorTestResponse executeProxy(TenantServiceEndpoint ep, TenantApiCredential cred,
                                               String path, String method, String requestBody) {
        String baseUrl = ep.getBaseUrl().endsWith("/") ? ep.getBaseUrl() : ep.getBaseUrl() + "/";
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String fullUrl = baseUrl + normalizedPath;
        ssrfGuard.validateExternal(fullUrl);

        AuthHeader auth = resolveAuthHeader(cred);

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
                if (auth != null) headers.set(auth.name(), auth.value());
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

    /**
     * Resolves the connector's outbound auth header from OpenBao, or {@code null} when the
     * connector requires no auth ({@code authScheme == NONE}). Mirrors the engine's
     * credential-consumer flow: look up the {@code tenant_credentials} metadata row by the
     * {@code (tenantCode, type, credentialRef)} triple, then read values from OpenBao.
     *
     * @throws ResponseStatusException 400 if a non-NONE connector has no bound credential or
     *         an unsupported authScheme; 404 if the bound credential is missing from the
     *         metadata index or has no value in OpenBao
     */
    private AuthHeader resolveAuthHeader(TenantApiCredential cred) {
        String scheme = cred.getAuthScheme();
        if ("NONE".equals(scheme)) {
            return null;
        }
        String credentialType = authSchemeToType(scheme);
        String ref = cred.getCredentialRef();
        if (ref == null || ref.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Connector '" + cred.getConnectorKey() + "' has no credential bound");
        }
        TenantCredential meta = tenantCredentialRepo
            .findByTenantIdAndCredentialTypeAndLabel(cred.getTenantCode(), credentialType, ref)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Bound credential not found: type=" + credentialType + " ref=" + ref));
        Map<String, Object> values = vault.read(meta.getVaultPath())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Credential has no value in vault: ref=" + ref));
        return buildAuthHeader(scheme, values);
    }

    /** Maps a connector authScheme to its canonical credential-type slug (Phase B.6). */
    private String authSchemeToType(String authScheme) {
        return switch (authScheme) {
            case "BASIC" -> "http-basic-auth";
            case "BEARER" -> "http-bearer-token";
            case "API_KEY" -> "http-header-auth";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported authScheme for credential resolution: " + authScheme);
        };
    }

    /** Builds the outbound auth header from OpenBao values for the given scheme. */
    private AuthHeader buildAuthHeader(String scheme, Map<String, Object> values) {
        return switch (scheme) {
            case "BASIC" -> {
                String token = Base64.getEncoder().encodeToString(
                    (str(values.get("username")) + ":" + str(values.get("password")))
                        .getBytes(StandardCharsets.UTF_8));
                yield new AuthHeader("Authorization", "Basic " + token);
            }
            case "BEARER" -> new AuthHeader("Authorization", "Bearer " + str(values.get("token")));
            case "API_KEY" -> new AuthHeader(str(values.get("headerName")), str(values.get("headerValue")));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported authScheme for credential resolution: " + scheme);
        };
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /** Outbound auth header name/value pair resolved from a tenant credential. */
    private record AuthHeader(String name, String value) {}

    @Transactional(readOnly = true)
    public Optional<String> resolveBaseUrl(String tenantCode, String connectorKey, String environment) {
        return endpointRepo
            .findByTenantCodeAndConnectorKeyAndEnvironment(tenantCode, connectorKey, environment)
            .filter(TenantServiceEndpoint::isActive)
            .map(TenantServiceEndpoint::getBaseUrl);
    }

    /**
     * Resolves the credential a registered connector is bound to, for the engine to apply in
     * connector mode (ADR-024 Model A). Admin owns the {@code authScheme → credential-type}
     * mapping ({@link #authSchemeToType}); the engine resolves the returned type/label against
     * OpenBao itself.
     *
     * <p>Returns {@link Optional#empty()} when the connector is unregistered, has
     * {@code authScheme=NONE}, or carries no {@code credentialRef} — all "apply no auth" cases.
     *
     * @throws ResponseStatusException 400 if the connector has an unsupported authScheme
     */
    @Transactional(readOnly = true)
    public Optional<ConnectorCredentialBindingResponse> resolveCredentialBinding(
            String tenantCode, String connectorKey) {
        return credentialRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .filter(cred -> !"NONE".equals(cred.getAuthScheme()))
            .filter(cred -> blankToNull(cred.getCredentialRef()) != null)
            .map(cred -> new ConnectorCredentialBindingResponse(
                authSchemeToType(cred.getAuthScheme()), cred.getCredentialRef()));
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Credential not found for connector: " + connectorKey));
    }

    private ConnectorResponse toResponse(TenantServiceEndpoint ep, TenantApiCredential cred) {
        return ConnectorResponse.builder()
            .endpointId(ep.getId())
            .tenantCode(ep.getTenantCode())
            .connectorKey(ep.getConnectorKey())
            .displayName(ep.getDisplayName())
            .baseUrl(ep.getBaseUrl())
            .environment(ep.getEnvironment())
            .active(ep.isActive())
            .connectorType(ep.getConnectorType())
            .authScheme(cred.getAuthScheme())
            .credentialRef(cred.getCredentialRef())
            .sampleSchema(ep.getSampleSchema())
            .createdAt(ep.getCreatedAt())
            .updatedAt(ep.getUpdatedAt())
            .build();
    }
}
