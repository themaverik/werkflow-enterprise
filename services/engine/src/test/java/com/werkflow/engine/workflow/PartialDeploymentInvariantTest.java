package com.werkflow.engine.workflow;

import com.werkflow.engine.controller.DmnDecisionController;
import com.werkflow.engine.controller.FormSchemaController;
import com.werkflow.engine.controller.ProcessDefinitionController;
import com.werkflow.engine.service.BundleDeploymentService;
import com.werkflow.engine.service.DmnDecisionService;
import com.werkflow.engine.service.FormSchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the partial-deployment invariant (ADR-033 / ADR-035).
 *
 * <p>Cross-artifact reference validation ({@link DeployReferenceValidator} — which 422s on a BPMN
 * whose referenced forms/decisions are missing) is the "logical deployment" check. It must apply
 * <strong>only</strong> when a BPMN is being deployed. Deploying a DMN on its own, or registering a
 * form on its own, must never be blocked by reference validation: forms and DMNs are independent,
 * reusable artifacts that legitimately exist before any BPMN references them.
 *
 * <p>This is asserted <strong>structurally</strong>: the validator is a dependency of the BPMN
 * deploy paths and of nothing else. A behavioural counterpart already exists
 * ({@link DeployReferenceValidatorTest} proves a ref-less BPMN does not throw); this test proves
 * the <em>wiring</em>. If a future change injects {@link DeployReferenceValidator} into the
 * DMN-only or form-only deploy paths, this test fails — surfacing the regression before it ships.
 */
class PartialDeploymentInvariantTest {

    /** True if {@code type} declares a field of type {@link DeployReferenceValidator}. */
    private static boolean dependsOnReferenceValidator(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(DeployReferenceValidator.class::equals);
    }

    @Test
    @DisplayName("BPMN deploy paths apply logical reference validation")
    void bpmnDeployPaths_useReferenceValidator() {
        assertThat(dependsOnReferenceValidator(BundleDeploymentService.class))
                .as("bundle deploy must validate BPMN form/DMN references")
                .isTrue();
        assertThat(dependsOnReferenceValidator(ProcessDefinitionController.class))
                .as("standalone BPMN deploy must validate form/DMN references")
                .isTrue();
    }

    @Test
    @DisplayName("DMN-only and form-only deploy paths are never blocked by reference validation")
    void partialDeployPaths_doNotUseReferenceValidator() {
        assertThat(dependsOnReferenceValidator(DmnDecisionService.class))
                .as("standalone DMN deploy must not require a BPMN / reference validation")
                .isFalse();
        assertThat(dependsOnReferenceValidator(DmnDecisionController.class))
                .as("DMN deploy endpoint must not require a BPMN / reference validation")
                .isFalse();
        assertThat(dependsOnReferenceValidator(FormSchemaService.class))
                .as("standalone form registration must not require a BPMN / reference validation")
                .isFalse();
        assertThat(dependsOnReferenceValidator(FormSchemaController.class))
                .as("form registration endpoint must not require a BPMN / reference validation")
                .isFalse();
    }
}
