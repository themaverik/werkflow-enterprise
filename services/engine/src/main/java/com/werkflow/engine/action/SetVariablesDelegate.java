package com.werkflow.engine.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.audit.ProcessAuditLog;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import com.werkflow.engine.security.el.FunctionRegistry;
import com.werkflow.engine.security.el.RestrictedExpressionManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.HasExpressionManagerEngineConfiguration;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SET_VARIABLES action block — assigns one or more process variables to literal values or
 * FEEL/JUEL expressions, without any external I/O.
 *
 * <p>Variable assignments are declared as {@code <flowable:field>} entries whose name follows
 * the pattern {@code var.<variableName>}. The field's string value is a literal; an expression
 * value (starting with {@code ${}) is evaluated against the current execution context.
 *
 * <p><b>Field-name conventions across action blocks:</b>
 * <ul>
 *   <li>{@code ab:*} — action-block kind-specific configuration (e.g. {@code ab:templateKey}
 *       for SEND_NOTIFICATION, {@code ab:connectorKey} for EXTERNAL_API_CALL). One delegate
 *       reads its own {@code ab:*} fields; never written to the process scope.</li>
 *   <li>{@code var.*} — generic process-variable assignments. Only this delegate
 *       (SET_VARIABLES) consumes them; each {@code var.<name>} field writes one variable
 *       into the process scope and disappears from the action-block contract.</li>
 * </ul>
 * The two prefixes are disjoint by intent: configuration vs. assignment.
 *
 * <p>Example BPMN fragment:
 * <pre>
 *   &lt;serviceTask flowable:delegateExpression="${setVariablesDelegate}"&gt;
 *     &lt;extensionElements&gt;
 *       &lt;flowable:field name="var.approvalStatus"&gt;
 *         &lt;flowable:string&gt;pending&lt;/flowable:string&gt;
 *       &lt;/flowable:field&gt;
 *       &lt;flowable:field name="var.assignedTo"&gt;
 *         &lt;flowable:expression&gt;${initiator}&lt;/flowable:expression&gt;
 *       &lt;/flowable:field&gt;
 *     &lt;/extensionElements&gt;
 *   &lt;/serviceTask&gt;
 * </pre>
 *
 * <p>All assignments are evaluated first, then applied to the execution in a single pass.
 * A partial commit never occurs — if any expression evaluation throws, no variables are written.
 *
 * <p>Delegate-Checklist.md compliance:
 * <ul>
 *   <li>Singleton {@code @Component} — no scope override</li>
 *   <li>Implements {@code JavaDelegate} — single {@code execute()} entry point</li>
 *   <li>No {@code @Async} — Flowable controls async via {@code flowable:async} on the element</li>
 *   <li>{@code expressionManager} injected via constructor from {@code ProcessEngineConfiguration} — no internal API</li>
 *   <li>No mutable instance state — all per-execution state read from {@code DelegateExecution}</li>
 *   <li>Dynamic fields accessed via {@code DelegateHelper.getFlowElementFields()} — not cached to instance</li>
 *   <li>Throws {@code IllegalStateException} on malformed field name (empty variable name)</li>
 *   <li>Logs executionId, processInstanceId, tenantId at INFO; variable names at INFO; values at DEBUG only</li>
 *   <li>Writes one {@link ProcessAuditLog} entry per execution containing variable
 *       names only (never values) — values may be sensitive. Audit-write failure
 *       is swallowed with ERROR log + {@code werkflow.engine.audit_failure} counter
 *       (workflow continues; audit drop is operational, not a business failure).</li>
 * </ul>
 */
@Slf4j
@Component("setVariablesDelegate")
public class SetVariablesDelegate implements JavaDelegate {

    private static final String VAR_PREFIX = "var.";
    private static final String ACTION_TYPE = "SET_VARIABLES";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExpressionManager expressionManager;
    private final ProcessAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    public SetVariablesDelegate(ProcessEngineConfiguration cfg,
                                ProcessAuditLogRepository auditLogRepository,
                                MeterRegistry meterRegistry) {
        this.expressionManager = ((HasExpressionManagerEngineConfiguration) cfg).getExpressionManager();
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void execute(DelegateExecution execution) {
        log.info("setVariablesDelegate: executing for processInstance={} execution={} tenant={}",
                execution.getProcessInstanceId(), execution.getId(), execution.getTenantId());

        List<FieldExtension> fields = DelegateHelper.getFlowElementFields(execution);

        // Filter to var.* fields only
        List<FieldExtension> varFields = fields.stream()
                .filter(f -> f.getFieldName() != null && f.getFieldName().startsWith(VAR_PREFIX))
                .toList();

        if (varFields.isEmpty()) {
            log.info("setVariablesDelegate: no var.* fields configured — no-op");
            return;
        }

        // Evaluate all assignments before writing any — prevents partial commit on error
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (FieldExtension field : varFields) {
            String variableName = field.getFieldName().substring(VAR_PREFIX.length());
            if (variableName.isBlank()) {
                throw new IllegalStateException(
                        "setVariablesDelegate: field name '" + field.getFieldName()
                                + "' has an empty variable name after 'var.' prefix");
            }
            Object value = resolveField(field, execution);
            resolved.put(variableName, value);
            log.debug("setVariablesDelegate: resolved var.{} = {}", variableName, value);
        }

        // Apply all assignments atomically (within Flowable's transaction context)
        for (Map.Entry<String, Object> entry : resolved.entrySet()) {
            execution.setVariable(entry.getKey(), entry.getValue());
            log.info("setVariablesDelegate: set variable '{}' processInstance={} tenant={}",
                    entry.getKey(), execution.getProcessInstanceId(), execution.getTenantId());
        }

        writeAuditLog(execution, new ArrayList<>(resolved.keySet()));
    }

    /**
     * Writes one audit log entry recording which variable names were assigned by this
     * execution. Values are intentionally not recorded — they may be sensitive and the
     * existing DEBUG-only value logging covers the development inspection case.
     *
     * <p>Variable names are stashed as JSON in the {@code responseBody} JSONB column —
     * the column is untyped at the DB layer and no engine-side consumer parses it with
     * an assumed HTTP shape, so this repurposing is safe. Shape: {@code {"variables":[...]}}.
     *
     * <p>Uses {@code saveAndFlush} (not {@code save}) so any DB-layer error surfaces
     * synchronously inside this method's catch block. With {@code save} alone, Hibernate
     * defers the INSERT until Flowable's tx commit; a flush failure would then bypass
     * this catch and roll back the variable writes that the prior loop committed.
     *
     * <p>Audit-write failure is swallowed: the workflow has already committed its
     * variables and continuing is the safer choice. The failure is surfaced via ERROR
     * log + {@code werkflow.engine.audit_failure} counter for operator visibility.
     */
    private void writeAuditLog(DelegateExecution execution, List<String> variableNames) {
        try {
            String namesJson = OBJECT_MAPPER.writeValueAsString(Map.of("variables", variableNames));
            ProcessAuditLog entry = ProcessAuditLog.builder()
                    .processInstanceId(execution.getProcessInstanceId())
                    .executionId(execution.getId())
                    .processDefinitionKey(extractKey(execution.getProcessDefinitionId()))
                    .actionType(ACTION_TYPE)
                    .taskId(execution.getCurrentActivityId())
                    .taskName(execution.getCurrentActivityName())
                    .initiatedBy(execution.getTenantId())
                    .timestamp(OffsetDateTime.now())
                    .responseBody(namesJson)
                    .responseTruncated(false)
                    .build();
            auditLogRepository.saveAndFlush(entry);
        } catch (Exception ex) {
            log.error("Audit log write failed for actionType={} executionId={} processInstanceId={}: {}",
                    ACTION_TYPE,
                    execution.getId(),
                    execution.getProcessInstanceId(),
                    ex.getMessage(),
                    ex);
            meterRegistry.counter("werkflow.engine.audit_failure", "actionType", ACTION_TYPE).increment();
        }
    }

    /**
     * Resolves a {@link FieldExtension} to a value. If the field has an expression, it is
     * evaluated against the current execution context using the full DATE_STRING_MATH function
     * bundle so SET_VARIABLES expressions can call {@code dateUtil.*}, {@code stringUtil.*},
     * and {@code mathUtil.*} helpers. Otherwise the string value is returned as-is.
     *
     * <p>Task C guarantees the engine ExpressionManager is always a
     * {@link RestrictedExpressionManager}. The {@code instanceof} guard is defensive: if
     * (unexpectedly) it is not, we fall back to plain {@code createExpression} so the delegate
     * never throws a ClassCastException.
     */
    private Object resolveField(FieldExtension field, DelegateExecution execution) {
        if (field.getExpression() != null && !field.getExpression().isBlank()) {
            Expression expr;
            if (expressionManager instanceof RestrictedExpressionManager rem) {
                expr = rem.compileWithFunctions(field.getExpression(), FunctionRegistry.DATE_STRING_MATH);
            } else {
                // fallback — preserves delegate behaviour if manager is unexpectedly replaced
                expr = expressionManager.createExpression(field.getExpression());
            }
            return expr.getValue(execution);
        }
        return field.getStringValue();
    }

    private String extractKey(String processDefinitionId) {
        if (processDefinitionId == null) return "unknown";
        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0] : processDefinitionId;
    }
}
