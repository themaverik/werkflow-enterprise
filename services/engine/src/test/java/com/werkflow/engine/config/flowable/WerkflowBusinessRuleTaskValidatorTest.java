package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.validation.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WerkflowBusinessRuleTaskValidator}: every {@code businessRuleTask} is
 * rejected (dead config in Flowable 7.2, ADR-026); a process without one is clean.
 *
 * @see WerkflowBusinessRuleTaskValidator
 */
class WerkflowBusinessRuleTaskValidatorTest {

    private final WerkflowBusinessRuleTaskValidator validator = new WerkflowBusinessRuleTaskValidator();

    @Test
    void validator_rejectsBusinessRuleTask() {
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        BusinessRuleTask brt = new BusinessRuleTask();
        brt.setId("evaluate");
        brt.setName("Evaluate");
        process.addFlowElement(brt);
        List<ValidationError> errors = new ArrayList<>();

        validator.executeValidation(bpmnModel, process, errors);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getProblem())
                .isEqualTo(WerkflowBusinessRuleTaskValidator.WERKFLOW_BUSINESS_RULE_TASK_UNSUPPORTED);
    }

    @Test
    void validator_emitsOneErrorPerBusinessRuleTask() {
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        BusinessRuleTask first = new BusinessRuleTask();
        first.setId("evaluate-1");
        BusinessRuleTask second = new BusinessRuleTask();
        second.setId("evaluate-2");
        process.addFlowElement(first);
        process.addFlowElement(second);
        List<ValidationError> errors = new ArrayList<>();

        validator.executeValidation(bpmnModel, process, errors);

        assertThat(errors).hasSize(2);
    }

    @Test
    void validator_allowsProcessWithoutBusinessRuleTask() {
        // The validator only checks for BusinessRuleTask presence — any non-BRT element is clean.
        // (A ServiceTask stands in for the ADR-026 replacement; its type value is irrelevant here.)
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("evaluate");
        process.addFlowElement(serviceTask);
        List<ValidationError> errors = new ArrayList<>();

        validator.executeValidation(bpmnModel, process, errors);

        assertThat(errors).isEmpty();
    }
}
