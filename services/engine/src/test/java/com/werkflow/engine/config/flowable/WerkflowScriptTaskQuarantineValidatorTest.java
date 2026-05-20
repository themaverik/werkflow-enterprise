package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.validation.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WerkflowScriptTaskQuarantineValidator}.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>A BPMN process containing a {@code scriptTask} element produces a
 *       {@link WerkflowScriptTaskQuarantineValidator#WERKFLOW_SCRIPT_TASK_QUARANTINED}
 *       validation error — aborting deployment.</li>
 *   <li>A BPMN process containing no {@code scriptTask} elements produces zero errors
 *       (sanity check).</li>
 * </ol>
 *
 * @see WerkflowScriptTaskQuarantineValidator
 */
class WerkflowScriptTaskQuarantineValidatorTest {

    // ------------------------------------------------------------------
    // Helper — builds a ScriptTask as the BPMN converter produces it
    // ------------------------------------------------------------------

    private ScriptTask groovyScriptTask() {
        ScriptTask scriptTask = new ScriptTask();
        scriptTask.setId("script-task-1");
        scriptTask.setName("Dangerous Groovy Task");
        scriptTask.setScriptFormat("groovy");
        scriptTask.setScript("execution.setVariable('x', 1)");
        return scriptTask;
    }

    // ------------------------------------------------------------------
    // Test 1 — scriptTask present → WERKFLOW_SCRIPT_TASK_QUARANTINED emitted
    // ------------------------------------------------------------------

    @Test
    void validator_rejectsGroovyScriptTask_withQuarantineError() {
        // Arrange
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        process.addFlowElement(groovyScriptTask());

        WerkflowScriptTaskQuarantineValidator validator = new WerkflowScriptTaskQuarantineValidator();
        List<ValidationError> errors = new ArrayList<>();

        // Act
        validator.executeValidation(bpmnModel, process, errors);

        // Assert
        assertThat(errors).hasSize(1);
        ValidationError error = errors.get(0);
        assertThat(error.getProblem())
                .isEqualTo(WerkflowScriptTaskQuarantineValidator.WERKFLOW_SCRIPT_TASK_QUARANTINED);
        assertThat(error.isWarning()).isFalse();
        assertThat(error.getDefaultDescription())
                .contains("ADR-016")
                .contains("script-task-1")
                .contains("groovy");
    }

    // ------------------------------------------------------------------
    // Test 2 — no scriptTask → zero errors (sanity)
    // ------------------------------------------------------------------

    @Test
    void validator_producesNoErrors_whenNoScriptTaskPresent() {
        // Arrange
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("clean-process");

        WerkflowScriptTaskQuarantineValidator validator = new WerkflowScriptTaskQuarantineValidator();
        List<ValidationError> errors = new ArrayList<>();

        // Act
        validator.executeValidation(bpmnModel, process, errors);

        // Assert
        assertThat(errors).isEmpty();
    }
}
