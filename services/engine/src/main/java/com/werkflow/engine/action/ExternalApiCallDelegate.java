package com.werkflow.engine.action;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.werkflow.common.security.SecretsResolver;
import com.werkflow.common.security.SsrfGuard;
import com.werkflow.engine.audit.ProcessAuditLog;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import java.net.http.HttpClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("externalApiCallDelegate")
public class ExternalApiCallDelegate implements JavaDelegate {

    private static final Pattern SAFE_JSONPATH =
        Pattern.compile("^\\$([.\\[][a-zA-Z0-9_.*@\\]\\[]+)*$");
    private static final int MAX_PATH_LENGTH = 200;
    private static final int MAX_RESPONSE_BYTES = 100 * 1024;

    private final SsrfGuard ssrfGuard;
    private final ResponseMasker responseMasker;
    private final SecretsResolver secretsResolver;
    private final ProcessAuditLogRepository auditLogRepository;
    private final RestClient restClient;
    private final TenantEndpointResolver endpointResolver;

    @Setter private Expression url;
    @Setter private Expression connector;
    @Setter private Expression path;
    @Setter private Expression body;
    @Setter private Expression method;
    @Setter private Expression secretRef;
    @Setter private Expression responseVariable;
    @Setter private Expression extractFields;
    @Setter private Expression maskFields;
    @Setter private Expression onError;
    /** When true, stores the raw (pre-mask) response as a transient variable named {@code <responseVar>Raw}. */
    @Setter private Expression storeRawResponse;
    /** When true, stores the masked response as a task-local variable (isolated to the current execution branch). */
    @Setter private Expression useLocalVariables;

    public ExternalApiCallDelegate(SsrfGuard ssrfGuard, ResponseMasker responseMasker,
                                   SecretsResolver secretsResolver,
                                   ProcessAuditLogRepository auditLogRepository,
                                   RestClient.Builder restClientBuilder,
                                   TenantEndpointResolver endpointResolver) {
        this.ssrfGuard = ssrfGuard;
        this.responseMasker = responseMasker;
        this.secretsResolver = secretsResolver;
        this.auditLogRepository = auditLogRepository;
        this.endpointResolver = endpointResolver;
        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.restClient = restClientBuilder
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
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

            // Default to POST when body present and no explicit method expression set
            if (bodyTemplate != null && !bodyTemplate.isBlank() && method == null) {
                methodValue = "POST";
            }
            if (methodValue == null || methodValue.isBlank()) {
                methodValue = "GET";
            }

            // URL resolution: connector+path mode (preferred) or legacy url mode
            String connectorKey = getString(connector, execution, null);
            if (connectorKey != null && !connectorKey.isBlank()) {
                String tenantCode = execution.getTenantId();
                if (tenantCode == null || tenantCode.isBlank()) {
                    throw new IllegalStateException(
                        "externalApiCallDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
                }
                String pathValue = getString(path, execution, "");
                String baseUrl = endpointResolver.resolve(tenantCode, connectorKey);
                // Normalize: ensure exactly one slash between base and path
                if (!pathValue.isEmpty() && !baseUrl.endsWith("/") && !pathValue.startsWith("/")) {
                    resolvedUrl = baseUrl + "/" + pathValue;
                } else if (!pathValue.isEmpty() && baseUrl.endsWith("/") && pathValue.startsWith("/")) {
                    resolvedUrl = baseUrl + pathValue.substring(1);
                } else {
                    resolvedUrl = baseUrl + pathValue;
                }
                ssrfGuard.validateExternal(resolvedUrl);
            } else {
                resolvedUrl = getString(url, execution, null);
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    throw new IllegalArgumentException(
                        "externalApiCallDelegate: no 'connector' or 'url' configured on task '"
                        + execution.getCurrentActivityId() + "'");
                }
                log.warn("externalApiCallDelegate: using deprecated 'url' field on task '{}'. " +
                         "Migrate to 'connector'+'path' fields.", execution.getCurrentActivityId());
                ssrfGuard.validate(resolvedUrl);
            }

            // 2. Resolve secret (only secretRef-based, never forward JWT)
            String secretKey = getString(secretRef, execution, null);
            String secretValue = secretKey != null && !secretKey.isBlank()
                ? secretsResolver.resolve(secretKey) : null;

            // 3. Resolve body template
            String resolvedBody = null;
            if (bodyTemplate != null && !bodyTemplate.isBlank()) {
                resolvedBody = resolveBodyTemplate(bodyTemplate, execution);
            }

            // 4. Make HTTP call
            long startTime = System.currentTimeMillis();
            HttpResult httpResult = makeHttpCall(resolvedUrl, methodValue, secretValue, resolvedBody);
            long durationMs = System.currentTimeMillis() - startTime;

            // 5. Truncate at 100KB
            String rawBody = httpResult.body();
            boolean truncated = false;
            if (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                rawBody = rawBody.substring(0, MAX_RESPONSE_BYTES);
                truncated = true;
            }

            // 6. Mask sensitive fields
            List<String> designerMaskFields = parseMaskFields(getString(maskFields, execution, null));
            String maskedBody = responseMasker.mask(rawBody, designerMaskFields);

            // 6a. Optionally store raw (pre-mask) body as a transient variable — not persisted to history
            boolean shouldStoreRaw = "true".equalsIgnoreCase(getString(storeRawResponse, execution, "false"));
            if (shouldStoreRaw) {
                execution.setTransientVariable(responseVar + "Raw", rawBody);
            }

            // 7. Extract named fields via JSONPath into process variables
            String extractSpec = getString(extractFields, execution, null);
            if (extractSpec != null && !extractSpec.isBlank()) {
                Map<String, String> extractions = parseExtractFields(extractSpec);
                applyExtractions(maskedBody, extractions, execution);
            }

            // 8. Store masked result — task-local when useLocalVariables=true (parallel branches), else global
            boolean shouldUseLocal = "true".equalsIgnoreCase(getString(useLocalVariables, execution, "false"));
            if (shouldUseLocal) {
                execution.setVariableLocal(responseVar, maskedBody);
            } else {
                execution.setVariable(responseVar, maskedBody);
            }

            // 9. Write audit log
            writeAuditLog(execution, resolvedUrl, methodValue, maskedBody, httpResult.status(),
                          durationMs, truncated, designerMaskFields, null);

        } catch (Exception e) {
            handleError(e, onErrorMode, responseVar, execution, resolvedUrl, methodValue);
        }
    }

    private record HttpResult(String body, int status) {}

    private static final Pattern BODY_TOKEN = Pattern.compile("\\$\\{([^}]+)\\}");

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

    private void handleError(Exception e, String onErrorMode, String responseVar,
                             DelegateExecution execution, String urlStr, String methodStr) {
        log.error("externalApiCallDelegate error [{}]: {}", onErrorMode, e.getMessage());
        writeAuditLog(execution, urlStr, methodStr, null, null, 0, false, List.of(), e.getMessage());

        switch (onErrorMode) {
            case "CONTINUE" -> {
                execution.setVariable(responseVar + "Success", false);
                execution.setVariable(responseVar + "Error", e.getMessage());
            }
            case "THROW_BPMN_ERROR" -> throw new BpmnError("EXTERNAL_API_ERROR", e.getMessage());
            default -> throw new RuntimeException("externalApiCallDelegate failed: " + e.getMessage(), e);
        }
    }

    private void writeAuditLog(DelegateExecution execution, String urlStr, String methodStr,
                               String responseBody, Integer status, long durationMs,
                               boolean truncated, List<String> maskedFields, String errorMessage) {
        try {
            ProcessAuditLog entry = ProcessAuditLog.builder()
                .processInstanceId(execution.getProcessInstanceId())
                .executionId(execution.getId())
                .processDefinitionKey(extractKey(execution.getProcessDefinitionId()))
                .actionType("EXTERNAL_API_CALL")
                .taskId(execution.getCurrentActivityId())
                .taskName(execution.getCurrentActivityName())
                .timestamp(OffsetDateTime.now())
                .requestUrl(urlStr)
                .requestMethod(methodStr)
                .requestHash(urlStr != null ? sha256(methodStr + urlStr) : null)
                .responseStatus(status)
                .responseBody(responseBody)
                .responseTruncated(truncated)
                .maskedFields(maskedFields)
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to write audit log: {}", ex.getMessage());
        }
    }

    // Package-private for testing
    Map<String, String> parseExtractFieldsForTest(String spec) {
        return parseExtractFields(spec);
    }

    private Map<String, String> parseExtractFields(String spec) {
        Map<String, String> result = new LinkedHashMap<>();
        if (spec == null || spec.isBlank()) return result;
        for (String entry : spec.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length == 2) {
                String path = parts[1].trim();
                try {
                    validateJsonPath(path);
                    result.put(parts[0].trim(), path);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping malformed extractFields entry '{}': {}", entry.trim(), e.getMessage());
                }
            } else {
                log.warn("Skipping malformed extractFields entry: '{}'", entry.trim());
            }
        }
        return result;
    }

    private void validateJsonPath(String path) {
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("JSONPath too long: " + path);
        }
        if (!SAFE_JSONPATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Disallowed JSONPath syntax: " + path);
        }
        JsonPath.compile(path);
    }

    private void applyExtractions(String maskedBody, Map<String, String> extractions,
                                  DelegateExecution execution) {
        if (maskedBody == null) return;
        Configuration conf = Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
        DocumentContext doc = JsonPath.using(conf).parse(maskedBody);
        for (Map.Entry<String, String> entry : extractions.entrySet()) {
            try {
                Object value = doc.read(entry.getValue());
                execution.setVariable(entry.getKey(), value);
            } catch (Exception e) {
                log.warn("Could not extract '{}' from response: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private List<String> parseMaskFields(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        return Arrays.stream(spec.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String getString(Expression expr, DelegateExecution execution, String defaultValue) {
        if (expr == null) return defaultValue;
        Object val = expr.getValue(execution);
        return val != null ? val.toString() : defaultValue;
    }

    private String extractKey(String processDefinitionId) {
        if (processDefinitionId == null) return "unknown";
        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0] : processDefinitionId;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
