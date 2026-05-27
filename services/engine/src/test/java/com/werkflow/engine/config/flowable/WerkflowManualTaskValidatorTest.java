package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ManualTask;
import org.flowable.bpmn.model.Process;
import org.flowable.validation.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WerkflowManualTaskValidator}: a {@code manualTask} carrying
 * {@code confirmationRequired=true} is rejected (ADR-017); plain or {@code false} manualTasks are
 * clean. The real-parser/nested-string coverage lives in {@code DeadConfigValidatorDeploymentTest}.
 *
 * @see WerkflowManualTaskValidator
 */
class WerkflowManualTaskValidatorTest {

    private final WerkflowManualTaskValidator validator = new WerkflowManualTaskValidator();

    private ManualTask manualTaskWithConfirmation(String value) {
        ManualTask manualTask = new ManualTask();
        manualTask.setId("confirm");
        manualTask.setName("Confirm");
        ExtensionElement field = new ExtensionElement();
        field.setName("field");
        field.addAttribute(attribute("name", "confirmationRequired"));
        field.addAttribute(attribute("value", value));
        manualTask.addExtensionElement(field);
        return manualTask;
    }

    private ExtensionAttribute attribute(String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute(name);
        attr.setValue(value);
        return attr;
    }

    private List<ValidationError> validate(ManualTask manualTask) {
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        process.addFlowElement(manualTask);
        List<ValidationError> errors = new ArrayList<>();
        validator.executeValidation(bpmnModel, process, errors);
        return errors;
    }

    @Test
    void validator_rejectsConfirmationRequiredTrue() {
        List<ValidationError> errors = validate(manualTaskWithConfirmation("true"));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getProblem())
                .isEqualTo(WerkflowManualTaskValidator.WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED);
    }

    @Test
    void validator_rejectsConfirmationRequiredUppercaseTrue() {
        // The validator uses equalsIgnoreCase — prove a hand-authored "TRUE" is still caught.
        List<ValidationError> errors = validate(manualTaskWithConfirmation("TRUE"));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getProblem())
                .isEqualTo(WerkflowManualTaskValidator.WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED);
    }

    @Test
    void validator_allowsConfirmationRequiredFalse() {
        assertThat(validate(manualTaskWithConfirmation("false"))).isEmpty();
    }

    @Test
    void validator_emitsOneErrorPerOffendingManualTask() {
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        ManualTask first = manualTaskWithConfirmation("true");
        first.setId("confirm-1");
        ManualTask second = manualTaskWithConfirmation("true");
        second.setId("confirm-2");
        process.addFlowElement(first);
        process.addFlowElement(second);
        List<ValidationError> errors = new ArrayList<>();

        validator.executeValidation(bpmnModel, process, errors);

        assertThat(errors).hasSize(2);
    }

    @Test
    void validator_allowsPlainManualTask() {
        ManualTask plain = new ManualTask();
        plain.setId("note");
        plain.setName("Note");

        assertThat(validate(plain)).isEmpty();
    }
}
