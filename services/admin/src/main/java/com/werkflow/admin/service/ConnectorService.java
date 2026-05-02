package com.werkflow.admin.service;

import com.werkflow.admin.dto.connector.*;
import com.werkflow.admin.entity.TenantApiCredential;
import com.werkflow.admin.entity.TenantServiceEndpoint;
import com.werkflow.admin.repository.TenantApiCredentialRepository;
import com.werkflow.admin.repository.TenantServiceEndpointRepository;
import com.werkflow.common.security.SecretsResolver;
import com.werkflow.common.security.SsrfGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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

    @Value("${app.engine-service.url:http://localhost:8080}")
    private String engineServiceUrl;

    private RestClient engineRestClient;

    private final TenantApiCredentialRepository credentialRepo;
    private final TenantServiceEndpointRepository endpointRepo;
    private final SecretsResolver secretsResolver;
    private final SsrfGuard ssrfGuard;

    @PostConstruct
    private void initEngineRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        this.engineRestClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
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
        validateSecretRef(request.getSecretRef());

        TenantServiceEndpoint ep = new TenantServiceEndpoint();
        ep.setTenantCode(request.getTenantCode());
        ep.setConnectorKey(request.getConnectorKey());
        ep.setServiceKey(request.getConnectorKey());
        ep.setDisplayName(request.getDisplayName());
        ep.setBaseUrl(request.getBaseUrl());
        ep.setEnvironment(request.getEnvironment());
        ep.setActive(request.isActive());
        ep.setSampleSchema(request.getSampleSchema());
        endpointRepo.save(ep);

        TenantApiCredential cred = new TenantApiCredential();
        cred.setTenantCode(request.getTenantCode());
        cred.setConnectorKey(request.getConnectorKey());
        cred.setCredentialKey(request.getConnectorKey());
        cred.setLabel(request.getDisplayName());
        cred.setAuthScheme(request.getAuthScheme());
        cred.setSecretRef(request.getSecretRef());
        cred.setHeaderName(request.getHeaderName());
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
            .stream()
            .findFirst()
            .map(TenantServiceEndpoint::getSampleSchema);
    }

    public ConnectorTestResponse testCall(String tenantCode, String connectorKey, ConnectorTestRequest request) {
        TenantServiceEndpoint ep = endpointRepo.findByTenantCodeAndConnectorKey(tenantCode, connectorKey)
            .stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("Connector not found: " + connectorKey));
        TenantApiCredential cred = findCredential(tenantCode, connectorKey);

        String fullUrl = ep.getBaseUrl() + request.getPath();
        ssrfGuard.validateExternal(fullUrl);

        String apiKey = secretsResolver.resolve(cred.getSecretRef());
        String authHeader = cred.getHeaderName() != null ? cred.getHeaderName() : "Authorization";
        String authValue = switch (cred.getAuthScheme()) {
            case "NONE" -> null;
            case "BEARER" -> "Bearer " + apiKey;
            case "API_KEY" -> apiKey;
            case "BASIC" -> "Basic " + apiKey;
            default -> apiKey;
        };

        RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();

        long start = Instant.now().toEpochMilli();
        final int[] statusHolder = {0};
        final Map<String, String> responseHeaders = new LinkedHashMap<>();
        final String[] bodyHolder = {""};

        var entity = restClient.method(HttpMethod.valueOf(request.getMethod()))
            .uri(fullUrl)
            .headers(headers -> {
                if (authValue != null) {
                    headers.set(authHeader, authValue);
                }
            })
            .retrieve()
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
        bodyHolder[0] = new String(truncated ? Arrays.copyOf(raw, MAX_RESPONSE_BYTES) : raw, StandardCharsets.UTF_8);

        long durationMs = Instant.now().toEpochMilli() - start;
        log.info("connector.test.call tenantCode={} connectorKey={} path={} method={} status={} durationMs={} truncated={}",
            tenantCode, connectorKey, request.getPath(), request.getMethod(),
            statusHolder[0], durationMs, truncated);

        return ConnectorTestResponse.builder()
            .statusCode(statusHolder[0])
            .headers(responseHeaders)
            .body(bodyHolder[0])
            .truncated(truncated)
            .durationMs(durationMs)
            .build();
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
            engineRestClient.post()
                .uri(uri)
                .retrieve()
                .toBodilessEntity();
            log.debug("Notified engine to invalidate cache for {}/{}", tenantCode, connectorKey);
        } catch (Exception e) {
            log.warn("Failed to notify engine cache invalidation for {}/{}: {}",
                     tenantCode, connectorKey, e.getMessage());
        }
    }

    private void validateSecretRef(String secretRef) {
        if (!secretsResolver.isKeyAllowed(secretRef)) {
            throw new IllegalArgumentException(
                "secretRef '" + secretRef + "' is not in werkflow.secrets.allowed-keys. " +
                "Add it to the service configuration before registering this connector.");
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
            .authScheme(cred.getAuthScheme())
            .headerName(cred.getHeaderName())
            .sampleSchema(ep.getSampleSchema())
            .createdAt(ep.getCreatedAt())
            .updatedAt(ep.getUpdatedAt())
            .build();
    }
}
