package com.werkflow.engine.action;

import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.action.credential.ConnectorCredentialBindingClient;
import com.werkflow.engine.action.credential.CredentialRegistry;
import com.werkflow.engine.action.credential.CredentialResolutionException;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.HttpCredentialType;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flowable JavaDelegate for outbound REST/HTTP calls from BPMN service tasks.
 *
 * <p>Previously named {@code externalApiCallDelegate}; both bean names are registered
 * so deployed BPMNs using the old name continue to work without modification.</p>
 *
 * <p>HTTP-specific concerns (URL resolution, SSRF guard, body templating, credential injection)
 * live here. Cross-cutting concerns (audit, masking, extraction, error dispatch) are
 * inherited from {@link ConnectorDelegateBase}.</p>
 *
 * <p>As of M4.12 Phase B.4, authentication is handled via {@code credentialType} +
 * {@code credentialRef} BPMN fields resolved through {@link CredentialRegistry}. The legacy
 * {@code secretRef} field is no longer supported — any BPMN using it will receive an
 * {@link IllegalStateException} at runtime with a clear migration message. See ADR-020
 * Phase B.4 Implementation Notes.</p>
 */
@Slf4j
@Component("externalApiCallDelegate")
public class RestConnectorDelegate extends ConnectorDelegateBase {

    private static final int MAX_RESPONSE_BYTES = 100 * 1024;
    private static final Pattern BODY_TOKEN = Pattern.compile("\\$\\{([^}]+)\\}");

    private static final String LEGACY_SECRETREF_ERROR =
        "secretRef field is no longer supported in M4.12 Phase B.4. " +
        "Replace with credentialType + credentialRef BPMN fields. See ADR-020 Phase B.4 Implementation Notes.";

    private final SsrfGuard ssrfGuard;
    private final HttpClient httpClient;
    private final TenantEndpointResolver endpointResolver;
    private final CredentialRegistry credentialRegistry;
    private final ConnectorCredentialBindingClient bindingClient;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public RestConnectorDelegate(SsrfGuard ssrfGuard,
                                 ResponseMasker responseMasker,
                                 CredentialRegistry credentialRegistry,
                                 ConnectorCredentialBindingClient bindingClient,
                                 ProcessAuditLogRepository auditLogRepository,
                                 MeterRegistry meterRegistry,
                                 TenantEndpointResolver endpointResolver) {
        super(responseMasker, auditLogRepository, meterRegistry);
        this.ssrfGuard = ssrfGuard;
        this.credentialRegistry = credentialRegistry;
        this.bindingClient = bindingClient;
        this.endpointResolver = endpointResolver;
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    protected String resolveActionType() {
        return "EXTERNAL_API_CALL";
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getFieldString(execution, "onError", "FAIL");
        String responseVar = getFieldString(execution, "responseVariable", "response");
        String resolvedUrl = null;
        String methodValue = null;

        try {
            // Hard-error guard: detect presence of legacy secretRef field in BPMN definition.
            // DelegateHelper.getFieldExpression returns non-null if the <flowable:field> element
            // exists — regardless of whether its value is blank or populated. This means we can
            // distinguish "field element present in BPMN" from "field element absent" using the
            // expression reference alone. We reject any BPMN containing the secretRef element,
            // even if blank, because a present element indicates a stale/unmigrated design.
            if (DelegateHelper.getFieldExpression(execution, "secretRef") != null) {
                throw new IllegalStateException(LEGACY_SECRETREF_ERROR);
            }

            methodValue = getFieldString(execution, "method", "GET").toUpperCase();
            String bodyTemplate = getFieldString(execution, "body", null);

            // Default to POST when body is present and no explicit method field
            if (bodyTemplate != null && !bodyTemplate.isBlank()
                    && DelegateHelper.getFieldExpression(execution, "method") == null) {
                methodValue = "POST";
            }
            if (methodValue == null || methodValue.isBlank()) {
                methodValue = "GET";
            }

            // URL resolution: connector+path mode (preferred) or legacy direct url.
            // Note: getFieldString() evaluates Flowable EL expressions before returning the string.
            // SSRF guard is applied AFTER full URL resolution (F7: guard must see the final URL).
            String connectorKey = getFieldString(execution, "connector", null);
            if (connectorKey != null && !connectorKey.isBlank()) {
                String tenantCode = execution.getTenantId();
                if (tenantCode == null || tenantCode.isBlank()) {
                    throw new IllegalStateException(
                        "restConnectorDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
                }
                String pathValue = getFieldString(execution, "path", "");
                String baseUrl = endpointResolver.resolve(tenantCode, connectorKey);
                resolvedUrl = joinUrl(baseUrl, pathValue);
            } else {
                resolvedUrl = getFieldString(execution, "url", null);
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    throw new IllegalArgumentException(
                        "restConnectorDelegate: no 'connector' or 'url' configured on task '"
                        + execution.getCurrentActivityId() + "'");
                }
                log.warn("restConnectorDelegate: using deprecated 'url' field on task '{}'. " +
                         "Migrate to 'connector'+'path' fields.", execution.getCurrentActivityId());
            }

            // Resolve body template (${varName} substitution)
            String resolvedBody = null;
            if (bodyTemplate != null && !bodyTemplate.isBlank()) {
                resolvedBody = resolveBodyTemplate(bodyTemplate, execution);
            }

            // Validate credential fields before SSRF — operator config errors must surface
            // immediately, not after a network validation. Resolution (vault read) happens later.
            String tenantCode = execution.getTenantId();
            HttpCredentialType resolvedCredType = validateCredentialConfig(execution);

            // F7: SSRF guard runs here — after full URL resolution and EL evaluation,
            // immediately before HTTP dispatch. resolvedUrl is the exact string sent on the wire.
            if (connectorKey != null && !connectorKey.isBlank()) {
                ssrfGuard.validateExternal(resolvedUrl);
            } else {
                ssrfGuard.validate(resolvedUrl);
            }

            // F1: resolve per-request timeout (default 30s, overridable via ab:timeoutSeconds)
            int timeoutSecs = DEFAULT_TIMEOUT_SECONDS;
            String timeoutStr = getFieldString(execution, "timeoutSeconds", null);
            if (timeoutStr != null && !timeoutStr.isBlank()) {
                try {
                    timeoutSecs = Integer.parseInt(timeoutStr.trim());
                } catch (NumberFormatException nfe) {
                    log.warn("restConnectorDelegate: invalid timeoutSeconds '{}', using default {}s",
                             timeoutStr, DEFAULT_TIMEOUT_SECONDS);
                }
            }

            HttpRequest.Builder reqBuilder = buildRequest(resolvedUrl, methodValue, resolvedBody, timeoutSecs);

            // Apply credential auth headers. Precedence (ADR-024): explicit per-task fields win
            // (direct-url escape hatch, B.4); otherwise, in connector mode, resolve the registered
            // connector's own credential server-side (Model A). Connectors with authScheme=NONE,
            // unregistered, or with no bound credential resolve to an empty binding → no auth.
            if (resolvedCredType != null) {
                String credentialRef = getFieldString(execution, "credentialRef", null);
                String credentialTypeName = getFieldString(execution, "credentialType", null);
                CredentialValues values = credentialRegistry.resolveForTenant(credentialTypeName, tenantCode, credentialRef);
                resolvedCredType.applyTo(reqBuilder, values);
            } else if (connectorKey != null && !connectorKey.isBlank()) {
                applyConnectorCredential(connectorKey, tenantCode, reqBuilder);
            }

            long startTime = System.currentTimeMillis();
            HttpResult httpResult = makeHttpCall(reqBuilder);
            long durationMs = System.currentTimeMillis() - startTime;

            // Truncate at 100KB
            String rawBody = httpResult.body();
            boolean truncated = false;
            if (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                rawBody = rawBody.substring(0, MAX_RESPONSE_BYTES);
                truncated = true;
            }

            List<String> designerMaskFields = parseMaskFields(getFieldString(execution, "maskFields", null));
            writeAuditLog(execution, resolvedUrl, methodValue,
                          responseMasker.mask(rawBody, designerMaskFields),
                          httpResult.status(), durationMs, truncated, designerMaskFields, null);

            storeResult(rawBody, execution);

        } catch (IllegalStateException | IllegalArgumentException | CredentialResolutionException e) {
            // Operator/credential configuration errors — not transient failures subject to onError
            // mode. A missing or rotated-away credential (CredentialResolutionException) must FAIL
            // the task, never silently degrade to an unauthenticated call under onError=CONTINUE
            // (ADR-024). Re-throw immediately so callers see the exact exception type.
            throw e;
        } catch (HttpTimeoutException e) {
            // F1: surface timeout as a named delegate failure so onError mode applies
            String msg = "HTTP timeout after " + (resolvedUrl != null ? getFieldString(execution, "timeoutSeconds", String.valueOf(DEFAULT_TIMEOUT_SECONDS)) : DEFAULT_TIMEOUT_SECONDS) + "s calling " + resolvedUrl;
            handleError(new RuntimeException(msg, e), onErrorMode, responseVar, execution, resolvedUrl + " " + methodValue);
        } catch (Exception e) {
            handleError(e, onErrorMode, responseVar, execution, resolvedUrl + " " + methodValue);
        }
    }

    // -------------------------------------------------------------------------
    // Credential validation
    // -------------------------------------------------------------------------

    /**
     * Validates the {@code credentialType} and {@code credentialRef} BPMN fields and returns
     * the resolved {@link HttpCredentialType}, or {@code null} if both fields are absent
     * (unauthenticated call).
     *
     * <p>This method is intentionally free of vault reads — it validates operator config only.
     * Vault resolution happens in {@code execute()} after this method returns.
     *
     * <p>Rules:
     * <ul>
     *   <li>Both blank → returns {@code null} (unauthenticated).</li>
     *   <li>Exactly one blank → {@link IllegalArgumentException} naming the missing field.</li>
     *   <li>Both present, type not {@link HttpCredentialType} → {@link IllegalArgumentException}.</li>
     *   <li>Both present, type valid → returns the {@link HttpCredentialType}.</li>
     * </ul>
     */
    private HttpCredentialType validateCredentialConfig(DelegateExecution execution) {
        String credentialType = getFieldString(execution, "credentialType", null);
        String credentialRef  = getFieldString(execution, "credentialRef",  null);

        boolean typeBlank = credentialType == null || credentialType.isBlank();
        boolean refBlank  = credentialRef  == null || credentialRef.isBlank();

        if (typeBlank && refBlank) {
            return null;
        }
        if (typeBlank) {
            throw new IllegalArgumentException(
                "restConnectorDelegate: 'credentialRef' is set but 'credentialType' is missing on task '"
                + execution.getCurrentActivityId() + "'");
        }
        if (refBlank) {
            throw new IllegalArgumentException(
                "restConnectorDelegate: 'credentialType' is set but 'credentialRef' is missing on task '"
                + execution.getCurrentActivityId() + "'");
        }

        CredentialType type = credentialRegistry.get(credentialType);
        if (!(type instanceof HttpCredentialType httpType)) {
            throw new IllegalArgumentException(
                "restConnectorDelegate: credential type '" + credentialType
                + "' does not implement HttpCredentialType — REST transport requires an HTTP credential");
        }
        return httpType;
    }

    /**
     * Applies the registered connector's own credential in connector mode (ADR-024 Model A).
     *
     * <p>Resolves the connector's {@code (credentialType, credentialRef)} binding from admin,
     * then resolves the values from OpenBao via {@link CredentialRegistry} and applies them.
     * No-op when the connector requires no auth (empty binding). The binding's type is always an
     * HTTP credential (admin only maps HTTP authSchemes); a non-HTTP type signals a registry
     * misconfiguration and is rejected.
     */
    private void applyConnectorCredential(String connectorKey, String tenantCode,
                                          HttpRequest.Builder reqBuilder) {
        bindingClient.resolveBinding(tenantCode, connectorKey).ifPresent(binding -> {
            CredentialType type = credentialRegistry.get(binding.credentialType());
            if (!(type instanceof HttpCredentialType httpType)) {
                throw new IllegalStateException(
                    "restConnectorDelegate: connector '" + connectorKey + "' is bound to credential type '"
                    + binding.credentialType() + "' which does not implement HttpCredentialType");
            }
            CredentialValues values = credentialRegistry.resolveForTenant(
                binding.credentialType(), tenantCode, binding.credentialRef());
            httpType.applyTo(reqBuilder, values);
        });
    }

    // -------------------------------------------------------------------------
    // Private HTTP helpers
    // -------------------------------------------------------------------------

    private record HttpResult(String body, int status) {}

    /**
     * Constructs an {@link HttpRequest.Builder} with URI, timeout, Accept header, optional
     * Content-Type, and HTTP method set. Auth headers are applied by the caller after
     * credential resolution.
     */
    private HttpRequest.Builder buildRequest(String urlStr, String methodStr,
                                             String bodyContent, int timeoutSecs) {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(urlStr))
            .timeout(Duration.ofSeconds(timeoutSecs))
            .header("Accept", "application/json");

        HttpRequest.BodyPublisher publisher = bodyContent != null
            ? HttpRequest.BodyPublishers.ofString(bodyContent, StandardCharsets.UTF_8)
            : HttpRequest.BodyPublishers.noBody();

        if (bodyContent != null) {
            reqBuilder.header("Content-Type", "application/json");
        }

        reqBuilder.method(methodStr, publisher);
        return reqBuilder;
    }

    private HttpResult makeHttpCall(HttpRequest.Builder reqBuilder)
            throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(reqBuilder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResult(response.body(), response.statusCode());
    }

    // Package-private for testing
    String resolveBodyTemplate(String template, DelegateExecution execution) {
        Matcher m = BODY_TOKEN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1).trim();
            Object val = execution.getVariable(varName);
            String replacement;
            if (val == null) {
                replacement = "null";
            } else if (val instanceof Number || val instanceof Boolean) {
                replacement = val.toString();
            } else {
                replacement = escapeJsonString(val.toString());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String escapeJsonString(String val) {
        StringBuilder sb = new StringBuilder(val.length());
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String joinUrl(String base, String path) {
        if (path == null || path.isEmpty()) return base;
        if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }
}
