package com.werkflow.engine.security.el;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.audit.ProcessAuditLog;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records EL evaluation denial events to the existing engine audit log.
 *
 * <p>Per audit doc {@code EL-Expression-Security.md} decisions D-EL-7 + D-EL-9:
 * the server-side audit retains the full unsanitised expression body, the caller
 * stack (processInstanceId, executionId, activityId, tenantId), and the variable
 * container shape (variable names + value types, never values). 90-day retention
 * policy is applied at the table level by the existing audit-log maintenance job —
 * no per-row TTL needed.
 *
 * <p>Reuses {@link ProcessAuditLog} with a new {@code action_type} discriminator
 * value {@code "EL_EXPRESSION_DENIED"}. No schema migration required.
 *
 * <p>Write failures are swallowed (logged + counter incremented). Audit-log drop
 * is operational — never a business failure. The same pattern is used by
 * {@code SetVariablesDelegate.writeAuditLog} (Service-Task.md F5).
 */
@Slf4j
@Component
public class ExpressionAuditLogger {

    public static final String ACTION_TYPE = "EL_EXPRESSION_DENIED";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProcessAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    public ExpressionAuditLogger(ProcessAuditLogRepository auditLogRepository,
                                 MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records one denial event. All parameters are server-side only — never
     * returned over the wire.
     *
     * @param code the deny code (sanitised in user-facing exception, full here)
     * @param siteId stable site identifier (e.g., {@code action-block:set-vars-1})
     * @param expressionText the full unsanitised expression body
     * @param processInstanceId nullable when not in an execution context
     * @param executionId nullable when not in an execution context
     * @param activityId nullable when not in an execution context
     * @param tenantId nullable when not in an execution context
     * @param variableTypes mapping of variable name → value type (never the value)
     */
    public void recordDenial(ExpressionErrorCode code,
                             String siteId,
                             String expressionText,
                             String processInstanceId,
                             String executionId,
                             String activityId,
                             String tenantId,
                             Map<String, String> variableTypes) {
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(buildPayload(code, siteId, expressionText, variableTypes));
            ProcessAuditLog entry = ProcessAuditLog.builder()
                    .processInstanceId(nullSafe(processInstanceId))
                    .executionId(nullSafe(executionId))
                    .processDefinitionKey("EL_EXPRESSION")
                    .actionType(ACTION_TYPE)
                    .taskId(activityId)
                    .initiatedBy(tenantId)
                    .timestamp(OffsetDateTime.now())
                    .responseBody(payload)
                    .responseTruncated(false)
                    .errorMessage(code.name())
                    .build();
            auditLogRepository.saveAndFlush(entry);
        } catch (JsonProcessingException ex) {
            failureMetric("payload_serialisation_failed");
            log.error("EL audit log write failed (payload serialisation): code={} siteId={}", code, siteId, ex);
        } catch (Exception ex) {
            failureMetric("repository_save_failed");
            log.error("EL audit log write failed (repository): code={} siteId={}", code, siteId, ex);
        }
    }

    private Map<String, Object> buildPayload(ExpressionErrorCode code,
                                             String siteId,
                                             String expressionText,
                                             Map<String, String> variableTypes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code.name());
        payload.put("siteId", siteId);
        payload.put("expression", expressionText);
        payload.put("variableTypes", variableTypes == null ? Map.of() : variableTypes);
        return payload;
    }

    private void failureMetric(String reason) {
        meterRegistry.counter("werkflow.engine.el_audit_failure", "reason", reason).increment();
    }

    private static String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }
}
