package com.werkflow.engine.action;

import com.werkflow.common.security.SecretsResolver;
import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flowable JavaDelegate for outbound REST/HTTP calls from BPMN service tasks.
 *
 * <p>Previously named {@code externalApiCallDelegate}; both bean names are registered
 * so deployed BPMNs using the old name continue to work without modification.</p>
 *
 * <p>HTTP-specific concerns (URL resolution, SSRF guard, body templating, secret injection)
 * live here. Cross-cutting concerns (audit, masking, extraction, error dispatch) are
 * inherited from {@link ConnectorDelegateBase}.</p>
 */
@Slf4j
@Component("externalApiCallDelegate")
public class RestConnectorDelegate extends ConnectorDelegateBase {

    private static final int MAX_RESPONSE_BYTES = 100 * 1024;
    private static final Pattern BODY_TOKEN = Pattern.compile("\\$\\{([^}]+)\\}");

    private final SsrfGuard ssrfGuard;
    private final RestClient restClient;
    private final TenantEndpointResolver endpointResolver;

    // -------------------------------------------------------------------------
    // REST-specific BPMN expression fields
    // -------------------------------------------------------------------------

    @Setter private Expression url;
    @Setter private Expression connector;
    @Setter private Expression path;
    @Setter private Expression body;
    @Setter private Expression method;
    @Setter private Expression secretRef;

    public RestConnectorDelegate(SsrfGuard ssrfGuard,
                                 ResponseMasker responseMasker,
                                 SecretsResolver secretsResolver,
                                 ProcessAuditLogRepository auditLogRepository,
                                 RestClient.Builder restClientBuilder,
                                 TenantEndpointResolver endpointResolver) {
        super(responseMasker, secretsResolver, auditLogRepository);
        this.ssrfGuard = ssrfGuard;
        this.endpointResolver = endpointResolver;
        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.restClient = restClientBuilder
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
    }

    @Override
    protected String resolveActionType() {
        return "EXTERNAL_API_CALL";
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getString(onError, execution, "FAIL");
        String responseVar = getString(responseVariable, execution, "response");
        String resolvedUrl = null;
        String methodValue = null;

        try {
            methodValue = getString(method, execution, "GET").toUpperCase();
            String bodyTemplate = getString(body, execution, null);

            // Default to POST when body is present and no explicit method field
            if (bodyTemplate != null && !bodyTemplate.isBlank() && method == null) {
                methodValue = "POST";
            }
            if (methodValue == null || methodValue.isBlank()) {
                methodValue = "GET";
            }

            // URL resolution: connector+path mode (preferred) or legacy direct url
            String connectorKey = getString(connector, execution, null);
            if (connectorKey != null && !connectorKey.isBlank()) {
                String tenantCode = execution.getTenantId();
                if (tenantCode == null || tenantCode.isBlank()) {
                    throw new IllegalStateException(
                        "restConnectorDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
                }
                String pathValue = getString(path, execution, "");
                String baseUrl = endpointResolver.resolve(tenantCode, connectorKey);
                resolvedUrl = joinUrl(baseUrl, pathValue);
                ssrfGuard.validateExternal(resolvedUrl);
            } else {
                resolvedUrl = getString(url, execution, null);
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    throw new IllegalArgumentException(
                        "restConnectorDelegate: no 'connector' or 'url' configured on task '"
                        + execution.getCurrentActivityId() + "'");
                }
                log.warn("restConnectorDelegate: using deprecated 'url' field on task '{}'. " +
                         "Migrate to 'connector'+'path' fields.", execution.getCurrentActivityId());
                ssrfGuard.validate(resolvedUrl);
            }

            // Resolve bearer secret
            String secretKey = getString(secretRef, execution, null);
            String secretValue = secretKey != null && !secretKey.isBlank()
                ? secretsResolver.resolve(secretKey) : null;

            // Resolve body template (${varName} substitution)
            String resolvedBody = null;
            if (bodyTemplate != null && !bodyTemplate.isBlank()) {
                resolvedBody = resolveBodyTemplate(bodyTemplate, execution);
            }

            long startTime = System.currentTimeMillis();
            HttpResult httpResult = makeHttpCall(resolvedUrl, methodValue, secretValue, resolvedBody);
            long durationMs = System.currentTimeMillis() - startTime;

            // Truncate at 100KB
            String rawBody = httpResult.body();
            boolean truncated = false;
            if (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                rawBody = rawBody.substring(0, MAX_RESPONSE_BYTES);
                truncated = true;
            }

            List<String> designerMaskFields = parseMaskFields(getString(maskFields, execution, null));
            writeAuditLog(execution, resolvedUrl, methodValue,
                          responseMasker.mask(rawBody, designerMaskFields),
                          httpResult.status(), durationMs, truncated, designerMaskFields, null);

            storeResult(rawBody, execution);

        } catch (Exception e) {
            handleError(e, onErrorMode, responseVar, execution, resolvedUrl + " " + methodValue);
        }
    }

    // -------------------------------------------------------------------------
    // Private HTTP helpers
    // -------------------------------------------------------------------------

    private record HttpResult(String body, int status) {}

    private HttpResult makeHttpCall(String urlStr, String methodStr, String secret, String bodyContent) {
        var spec = restClient.method(HttpMethod.valueOf(methodStr)).uri(urlStr);
        if (secret != null) {
            spec = spec.header("Authorization", "Bearer " + secret);
        }
        if (bodyContent != null) {
            spec = spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(bodyContent);
        }
        return spec.exchange((req, res) -> {
            int status = res.getStatusCode().value();
            String responseBody;
            try (var is = res.getBody()) {
                responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new HttpResult(responseBody, status);
        });
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
