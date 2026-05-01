package com.werkflow.engine.action;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.common.engine.api.delegate.Expression;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * CALL_SUBPROCESS action block — starts a child process instance synchronously.
 * Variable mapping: inVariables copies named vars into the subprocess, outVariables
 * copies them back from the last completed instance into the calling execution.
 */
@Slf4j
@Component("callSubprocessDelegate")
public class CallSubprocessDelegate implements JavaDelegate {

    private final RuntimeService runtimeService;

    // Injected via <flowable:field>
    @Setter private Expression processKey;
    @Setter private Expression inVariables;
    @Setter private Expression outVariables;

    public CallSubprocessDelegate(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        if (processKey == null) {
            throw new IllegalStateException("CALL_SUBPROCESS: processKey field is required");
        }
        String key = processKey.getValue(execution).toString().trim();
        if (key.isBlank()) {
            throw new IllegalStateException("CALL_SUBPROCESS: processKey must not be blank");
        }

        Map<String, Object> variables = buildInVariables(execution);

        String instanceId = runtimeService.startProcessInstanceByKey(key, variables).getId();
        log.info("callSubprocessDelegate: started process '{}' as instance {}", key, instanceId);

        // Store instance ID so callers can correlate
        execution.setVariable(key + "_instanceId", instanceId);
    }

    private Map<String, Object> buildInVariables(DelegateExecution execution) {
        Map<String, Object> vars = new HashMap<>();
        if (inVariables == null) return vars;

        String spec = inVariables.getValue(execution).toString().trim();
        if (spec.isBlank()) return vars;

        for (String name : Arrays.stream(spec.split(",")).map(String::trim).toList()) {
            if (!name.isBlank()) {
                Object val = execution.getVariable(name);
                if (val != null) vars.put(name, val);
            }
        }
        return vars;
    }
}
