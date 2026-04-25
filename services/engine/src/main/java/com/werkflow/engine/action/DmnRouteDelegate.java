package com.werkflow.engine.action;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates a DMN decision table from within a BPMN service task.
 *
 * Replaces {@code <businessRuleTask flowable:decisionRef="...">} for BPMNs deployed via the
 * REST API. Flowable's standard businessRuleTask implementation requires Drools/KIE classes that
 * are not on the classpath; this delegate calls Flowable's DmnDecisionService directly.
 *
 * Usage in BPMN:
 * <pre>
 *   &lt;serviceTask id="routeLeave" name="Route Leave"
 *                flowable:delegateExpression="${dmnRouteDelegate}"&gt;
 *     &lt;extensionElements&gt;
 *       &lt;flowable:field name="decisionRef"&gt;&lt;flowable:string&gt;leave-routing&lt;/flowable:string&gt;&lt;/flowable:field&gt;
 *       &lt;!-- mapDecisionResult: outputVariables | singleEntry --&gt;
 *       &lt;flowable:field name="mapDecisionResult"&gt;&lt;flowable:string&gt;singleEntry&lt;/flowable:string&gt;&lt;/flowable:field&gt;
 *       &lt;!-- resultVariable: variable name for singleEntry result (ignored for outputVariables) --&gt;
 *       &lt;flowable:field name="resultVariable"&gt;&lt;flowable:string&gt;approverLevel&lt;/flowable:string&gt;&lt;/flowable:field&gt;
 *     &lt;/extensionElements&gt;
 *   &lt;/serviceTask&gt;
 * </pre>
 *
 * mapDecisionResult modes:
 *   outputVariables — each output column from the first matched rule is set as a process variable
 *   singleEntry     — the value of the first output column of the first matched rule is set
 *                     into the process variable named by resultVariable
 */
@Slf4j
@Component("dmnRouteDelegate")
public class DmnRouteDelegate implements JavaDelegate {

    private final DmnDecisionService dmnDecisionService;

    /** DMN decision key to evaluate (required). */
    @Setter
    private Expression decisionRef;

    /**
     * How to map the DMN result into process variables.
     * Supported: "outputVariables" (default) | "singleEntry"
     */
    @Setter
    private Expression mapDecisionResult;

    /** Target process variable name when mapDecisionResult = singleEntry. */
    @Setter
    private Expression resultVariable;

    public DmnRouteDelegate(DmnDecisionService dmnDecisionService) {
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String decisionKey = resolveString(decisionRef, execution, "decisionRef");
        String mapping     = resolveString(mapDecisionResult, execution, "outputVariables");
        String resultVar   = resolveString(resultVariable, execution, null);

        log.info("DmnRouteDelegate: evaluating decision '{}' (mapping={}) for process instance {}",
                decisionKey, mapping, execution.getProcessInstanceId());

        // Collect ALL current process variables as DMN input variables
        Map<String, Object> inputVariables = new HashMap<>(execution.getVariables());

        // Resolve tenantId: try process execution tenant first, fall back to "default"
        String tenantId = execution.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        try {
            Map<String, Object> result = dmnDecisionService
                    .createExecuteDecisionBuilder()
                    .decisionKey(decisionKey)
                    .tenantId(tenantId)
                    .variables(inputVariables)
                    .executeWithSingleResult();

            if (result == null || result.isEmpty()) {
                log.warn("DmnRouteDelegate: decision '{}' returned no matching rule for inputs: {}",
                        decisionKey, inputVariables.keySet());
                return;
            }

            log.debug("DmnRouteDelegate: decision '{}' result: {}", decisionKey, result);

            if ("singleEntry".equals(mapping)) {
                if (resultVar == null || resultVar.isBlank()) {
                    throw new IllegalStateException(
                            "DmnRouteDelegate: resultVariable must be set when mapDecisionResult=singleEntry");
                }
                // Take the value of the first output column
                Object value = result.values().iterator().next();
                execution.setVariable(resultVar, value);
                log.info("DmnRouteDelegate: set {}={} (singleEntry from decision '{}')",
                        resultVar, value, decisionKey);
            } else {
                // outputVariables (default): each key-value becomes a process variable
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    execution.setVariable(entry.getKey(), entry.getValue());
                    log.info("DmnRouteDelegate: set {}={} (outputVariables from decision '{}')",
                            entry.getKey(), entry.getValue(), decisionKey);
                }
            }

        } catch (Exception e) {
            log.error("DmnRouteDelegate: failed to evaluate decision '{}': {}", decisionKey, e.getMessage(), e);
            throw new RuntimeException("DMN evaluation failed for decision '" + decisionKey + "': " + e.getMessage(), e);
        }
    }

    private String resolveString(Expression expr, DelegateExecution execution, String defaultValue) {
        if (expr == null) return defaultValue;
        Object val = expr.getValue(execution);
        return val != null ? val.toString().trim() : defaultValue;
    }
}
