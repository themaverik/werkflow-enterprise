package com.werkflow.engine.action;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.werkflow.common.security.SecretsResolver;
import com.werkflow.engine.audit.ProcessAuditLog;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Abstract base for all connector delegates. Centralises cross-cutting behaviour
 * that would otherwise be duplicated across REST, database, and future transport
 * types: audit logging, error-mode dispatch, JSONPath extraction, field masking,
 * and response variable storage.
 *
 * <p>Concrete delegates call {@link #storeResult}, {@link #handleError}, and
 * {@link #writeAuditLog} — they are responsible only for the transport-specific
 * execution (HTTP call, JDBC query, etc.).</p>
 */
@Slf4j
public abstract class ConnectorDelegateBase implements JavaDelegate {

    protected static final Pattern SAFE_JSONPATH =
        Pattern.compile("^\\$([.\\[][a-zA-Z0-9_.*@\\]\\[]+)*$");
    protected static final int MAX_PATH_LENGTH = 200;

    protected final ResponseMasker responseMasker;
    protected final SecretsResolver secretsResolver;
    protected final ProcessAuditLogRepository auditLogRepository;
    protected final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // BPMN expression fields common to all connector delegates
    // -------------------------------------------------------------------------

    /** Process variable name to store the connector response into. Default: "response". */
    @Setter protected Expression responseVariable;

    /**
     * Comma-separated list of {@code varName:$.jsonpath} pairs to extract
     * from the response and store as individual process variables.
     */
    @Setter protected Expression extractFields;

    /**
     * Comma-separated list of field names to mask in the stored response.
     * Delegated to {@link ResponseMasker}.
     */
    @Setter protected Expression maskFields;

    /**
     * Error handling mode: {@code FAIL} (default), {@code CONTINUE}, or
     * {@code THROW_BPMN_ERROR}. Determines what happens when the connector
     * call raises an exception.
     */
    @Setter protected Expression onError;

    /**
     * When {@code true}, stores the raw (pre-mask) response body as a transient
     * variable named {@code <responseVar>Raw}. Not persisted to history.
     */
    @Setter protected Expression storeRawResponse;

    /**
     * When {@code true}, stores the result as a task-local variable (isolated to
     * the current parallel execution branch) rather than a global process variable.
     */
    @Setter protected Expression useLocalVariables;

    protected ConnectorDelegateBase(ResponseMasker responseMasker,
                                    SecretsResolver secretsResolver,
                                    ProcessAuditLogRepository auditLogRepository,
                                    MeterRegistry meterRegistry) {
        this.responseMasker = responseMasker;
        this.secretsResolver = secretsResolver;
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
    }

    // -------------------------------------------------------------------------
    // Protected helpers — called by concrete subclasses
    // -------------------------------------------------------------------------

    /**
     * Applies masking + extraction + variable storage for a successful result.
     *
     * <p>Concrete delegates obtain the raw response string from their transport,
     * then delegate to this method so that all masking/extraction/storage logic
     * stays in one place regardless of transport type.</p>
     *
     * @param rawBody     raw response body (may be null)
     * @param execution   current BPMN execution context
     */
    protected void storeResult(String rawBody, DelegateExecution execution) {
        String responseVar = getString(responseVariable, execution, "response");
        List<String> designerMaskFields = parseMaskFields(getString(maskFields, execution, null));

        String maskedBody = responseMasker.mask(rawBody, designerMaskFields);

        // Optionally keep pre-mask body as a transient (non-historical) variable
        boolean shouldStoreRaw = "true".equalsIgnoreCase(getString(storeRawResponse, execution, "false"));
        if (shouldStoreRaw) {
            execution.setTransientVariable(responseVar + "Raw", rawBody);
        }

        // Extract named fields via JSONPath before writing the final variable
        String extractSpec = getString(extractFields, execution, null);
        if (extractSpec != null && !extractSpec.isBlank()) {
            Map<String, String> extractions = parseExtractFields(extractSpec);
            applyExtractions(maskedBody, extractions, execution);
        }

        // Honour local-variable scope for parallel branch isolation
        boolean shouldUseLocal = "true".equalsIgnoreCase(getString(useLocalVariables, execution, "false"));
        if (shouldUseLocal) {
            execution.setVariableLocal(responseVar, maskedBody);
        } else {
            execution.setVariable(responseVar, maskedBody);
        }
    }

    /**
     * Dispatches connector errors according to the configured {@code onError} mode.
     *
     * <ul>
     *   <li>{@code CONTINUE} — stores {@code <var>Success=false} and {@code <var>Error=msg}</li>
     *   <li>{@code THROW_BPMN_ERROR} — raises a BPMN error boundary catch event</li>
     *   <li>{@code FAIL} (default) — propagates as a runtime exception, aborting the execution</li>
     * </ul>
     */
    protected void handleError(Exception e, String onErrorMode, String responseVar,
                               DelegateExecution execution, String contextInfo) {
        log.error("{} error [{}] ctx={}: {}", getClass().getSimpleName(), onErrorMode, contextInfo, e.getMessage());
        writeAuditLog(execution, contextInfo, null, null, null, 0, false, List.of(), e.getMessage());

        switch (onErrorMode) {
            case "CONTINUE" -> {
                execution.setVariable(responseVar + "Success", false);
                execution.setVariable(responseVar + "Error", e.getMessage());
            }
            case "THROW_BPMN_ERROR" -> throw new BpmnError("CONNECTOR_ERROR", e.getMessage());
            default -> throw new RuntimeException(getClass().getSimpleName() + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Writes an audit log entry for a connector invocation (success or failure).
     * Failures here are swallowed and logged — an audit failure must never abort a workflow.
     */
    protected void writeAuditLog(DelegateExecution execution, String requestInfo,
                                 String methodOrOperation, String responseBody,
                                 Integer status, long durationMs, boolean truncated,
                                 List<String> maskedFields, String errorMessage) {
        try {
            ProcessAuditLog entry = ProcessAuditLog.builder()
                .processInstanceId(execution.getProcessInstanceId())
                .executionId(execution.getId())
                .processDefinitionKey(extractKey(execution.getProcessDefinitionId()))
                .actionType(resolveActionType())
                .taskId(execution.getCurrentActivityId())
                .taskName(execution.getCurrentActivityName())
                .timestamp(OffsetDateTime.now())
                .requestUrl(requestInfo)
                .requestMethod(methodOrOperation)
                .requestHash(requestInfo != null && methodOrOperation != null
                    ? sha256(methodOrOperation + requestInfo) : null)
                .responseStatus(status)
                .responseBody(responseBody)
                .responseTruncated(truncated)
                .maskedFields(maskedFields)
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // F2: audit failure is NOT a business failure — workflow continues.
            // Operators MUST see this: log at ERROR with full trace context + alert via counter.
            log.error("Audit log write failed for actionType={} executionId={} processInstanceId={} activityInstanceId={}: {}",
                resolveActionType(),
                execution.getId(),
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                ex.getMessage(),
                ex);
            meterRegistry.counter("werkflow.connector.audit_failure", "actionType", resolveActionType()).increment();
        }
    }

    /**
     * Returns the audit log action type for this delegate. Subclasses override
     * to emit a more specific type (e.g. "DATABASE_CONNECTOR_CALL").
     */
    protected String resolveActionType() {
        return "CONNECTOR_CALL";
    }

    // -------------------------------------------------------------------------
    // Shared field-level utilities
    // -------------------------------------------------------------------------

    /**
     * Parses the {@code extractFields} expression into a map of
     * {@code variableName -> JSONPath} pairs. Malformed entries are skipped with a warning.
     */
    protected Map<String, String> parseExtractFields(String spec) {
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
                log.warn("Skipping malformed extractFields entry (missing colon): '{}'", entry.trim());
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

    protected List<String> parseMaskFields(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        return Arrays.stream(spec.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Null-safe expression evaluator. Returns {@code defaultValue} when the expression
     * is null or evaluates to null.
     */
    protected String getString(Expression expr, DelegateExecution execution, String defaultValue) {
        if (expr == null) return defaultValue;
        Object val = expr.getValue(execution);
        return val != null ? val.toString() : defaultValue;
    }

    // -------------------------------------------------------------------------
    // Private utilities
    // -------------------------------------------------------------------------

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
