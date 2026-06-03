package com.werkflow.engine.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ExecutionListener that starts a linked process instance when an event fires.
 *
 * Reads flowable:triggerProcess from the current BPMN element's custom attributes.
 * Registered in the BPMN designer as an executionListener with
 * delegateExpression="${processCallDelegate}".
 *
 * Error handling:
 * - Null/blank key: no-op, parent process continues
 * - Process key not found: logs warning, parent process continues
 * - Any other exception: rethrown to fail the execution
 */
@Slf4j
@Component("processCallDelegate")
@RequiredArgsConstructor
public class ProcessCallDelegate implements ExecutionListener {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private final RuntimeService runtimeService;

    @Override
    public void notify(DelegateExecution execution) {
        FlowElement flowElement = execution.getCurrentFlowElement();
        String key = flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess");

        if (key == null || key.isBlank()) {
            return;
        }

        Map<String, Object> vars = execution.getVariables();
        try {
            runtimeService.startProcessInstanceByKeyAndTenantId(key, vars, execution.getTenantId());
            log.info("ProcessCallDelegate: started process '{}' from element '{}'",
                key, flowElement.getId());
        } catch (FlowableObjectNotFoundException e) {
            log.warn("ProcessCallDelegate: process definition '{}' not found — skipping trigger on element '{}'",
                key, flowElement.getId());
        } catch (Exception e) {
            log.error("ProcessCallDelegate: failed to start process '{}' from element '{}': {}",
                key, flowElement.getId(), e.getMessage());
            throw e;
        }
    }
}
