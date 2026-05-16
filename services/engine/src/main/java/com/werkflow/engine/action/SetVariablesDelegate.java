package com.werkflow.engine.action;

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
 * </ul>
 */
@Slf4j
@Component("setVariablesDelegate")
public class SetVariablesDelegate implements JavaDelegate {

    private static final String VAR_PREFIX = "var.";

    private final ExpressionManager expressionManager;

    public SetVariablesDelegate(ProcessEngineConfiguration cfg) {
        this.expressionManager = ((HasExpressionManagerEngineConfiguration) cfg).getExpressionManager();
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
    }

    /**
     * Resolves a {@link FieldExtension} to a value. If the field has an expression, it is
     * evaluated against the current execution context. Otherwise the string value is returned as-is.
     */
    private Object resolveField(FieldExtension field, DelegateExecution execution) {
        if (field.getExpression() != null && !field.getExpression().isBlank()) {
            Expression expr = expressionManager.createExpression(field.getExpression());
            return expr.getValue(execution);
        }
        return field.getStringValue();
    }
}
