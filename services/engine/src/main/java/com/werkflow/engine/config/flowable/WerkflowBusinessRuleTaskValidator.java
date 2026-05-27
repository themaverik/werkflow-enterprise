package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
import org.flowable.bpmn.model.Process;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.ProcessLevelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hard-rejects any {@code <bpmn:businessRuleTask>} element encountered during BPMN deployment.
 *
 * <p>{@code businessRuleTask} is dead config in Flowable 7.2: the engine routes it to the legacy
 * Drools / KIE rule behaviour, never to {@code DmnActivityBehavior}. Werkflow does not ship Drools,
 * so a {@code businessRuleTask} either fails to wire at deploy ({@code NoClassDefFoundError
 * org.kie...}) or throws at runtime — it never evaluates a DMN decision. A DMN decision is invoked
 * only by {@code <serviceTask flowable:type="dmn">} + a {@code decisionTableReferenceKey} field
 * extension. (Camunda binds DMN to {@code businessRuleTask}; Flowable does not — see ADR-026.)
 *
 * <p>This validator turns that silent dead-config into a loud, actionable deployment error, in sync
 * with the designer (which only emits the {@code serviceTask} form). It mirrors
 * {@link WerkflowScriptTaskQuarantineValidator}. Zero deployed BPMN uses {@code businessRuleTask},
 * so no live process breaks.
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-026-deployment-bundle-versioning.md">ADR-026</a>
 */
public class WerkflowBusinessRuleTaskValidator extends ProcessLevelValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowBusinessRuleTaskValidator.class);

    /** Stable error code emitted for every rejected businessRuleTask element. */
    public static final String WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED = "WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED";

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        List<BusinessRuleTask> businessRuleTasks = process.findFlowElementsOfType(BusinessRuleTask.class);
        for (BusinessRuleTask businessRuleTask : businessRuleTasks) {
            String id = businessRuleTask.getId();
            String name = businessRuleTask.getName();

            LOGGER.warn("WerkflowBusinessRuleTaskValidator: rejecting businessRuleTask id='{}' name='{}' "
                    + "in process '{}' — dead config in Flowable 7.2 (ADR-026)", id, name, process.getId());

            String message = String.format(
                    "businessRuleTask is not supported (ADR-026): in Flowable 7.2 it routes to the "
                            + "legacy Drools rule engine and never evaluates a DMN decision. "
                            + "Element id=%s, name='%s'. Author the decision as "
                            + "<serviceTask flowable:type=\"dmn\"> with a decisionTableReferenceKey field instead.",
                    id, name);

            addError(errors, WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED, process, businessRuleTask, message);
        }
    }
}
