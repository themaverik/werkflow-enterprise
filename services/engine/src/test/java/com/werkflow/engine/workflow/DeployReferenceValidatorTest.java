package com.werkflow.engine.workflow;

import com.werkflow.engine.exception.DanglingReferenceException;
import com.werkflow.engine.service.DmnDecisionService;
import com.werkflow.engine.service.FormSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeployReferenceValidator} — verifies aggregated fail-loud behaviour
 * when forms or decisions referenced in a BPMN do not exist for the deploying tenant.
 */
class DeployReferenceValidatorTest {

    private static final String TENANT = "acme";

    private BpmnBundleRefExtractor refExtractor;
    private FormSchemaService formSchemaService;
    private DmnDecisionService dmnDecisionService;
    private DeployReferenceValidator validator;

    /** Minimal BPMN with one form key and one DMN decision reference. */
    private static final String BPMN_WITH_FORM_AND_DECISION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="test-process">
                <startEvent id="start" flowable:formKey="my-form"/>
                <serviceTask id="dmn1" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey">
                      <flowable:string>my-decision</flowable:string>
                    </flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

    /** BPMN with two form keys and two DMN decision refs. */
    private static final String BPMN_WITH_TWO_FORMS_TWO_DECISIONS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="test-process">
                <startEvent id="start" flowable:formKey="form-a"/>
                <userTask id="ut1" flowable:formKey="form-b"/>
                <serviceTask id="dmn1" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey">
                      <flowable:string>decision-a</flowable:string>
                    </flowable:field>
                  </extensionElements>
                </serviceTask>
                <serviceTask id="dmn2" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey">
                      <flowable:string>decision-b</flowable:string>
                    </flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

    @BeforeEach
    void setUp() {
        refExtractor = new BpmnBundleRefExtractor();
        formSchemaService = mock(FormSchemaService.class);
        dmnDecisionService = mock(DmnDecisionService.class);
        validator = new DeployReferenceValidator(refExtractor, formSchemaService, dmnDecisionService);
    }

    @Test
    @DisplayName("does not throw when all referenced forms and decisions exist")
    void allRefsExist_noException() {
        when(formSchemaService.formExistsActiveVersion("my-form", TENANT)).thenReturn(true);
        when(dmnDecisionService.decisionExists("my-decision", TENANT)).thenReturn(true);

        assertThatCode(() -> validator.validate(BPMN_WITH_FORM_AND_DECISION, TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throws with the missing form key when only the form is absent")
    void missingForm_throwsWithFormKey() {
        when(formSchemaService.formExistsActiveVersion("my-form", TENANT)).thenReturn(false);
        when(dmnDecisionService.decisionExists("my-decision", TENANT)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(BPMN_WITH_FORM_AND_DECISION, TENANT))
                .isInstanceOf(DanglingReferenceException.class)
                .satisfies(e -> {
                    DanglingReferenceException ex = (DanglingReferenceException) e;
                    assertThat(ex.getMissingForms()).containsExactly("my-form");
                    assertThat(ex.getMissingDecisions()).isEmpty();
                });
    }

    @Test
    @DisplayName("throws with the missing decision key when only the decision is absent")
    void missingDecision_throwsWithDecisionKey() {
        when(formSchemaService.formExistsActiveVersion("my-form", TENANT)).thenReturn(true);
        when(dmnDecisionService.decisionExists("my-decision", TENANT)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(BPMN_WITH_FORM_AND_DECISION, TENANT))
                .isInstanceOf(DanglingReferenceException.class)
                .satisfies(e -> {
                    DanglingReferenceException ex = (DanglingReferenceException) e;
                    assertThat(ex.getMissingForms()).isEmpty();
                    assertThat(ex.getMissingDecisions()).containsExactly("my-decision");
                });
    }

    @Test
    @DisplayName("aggregates ALL missing forms and decisions in one throw — does not fail on first missing ref")
    void aggregatesBothMissingFormsAndDecisions() {
        when(formSchemaService.formExistsActiveVersion("form-a", TENANT)).thenReturn(false);
        when(formSchemaService.formExistsActiveVersion("form-b", TENANT)).thenReturn(false);
        when(dmnDecisionService.decisionExists("decision-a", TENANT)).thenReturn(false);
        when(dmnDecisionService.decisionExists("decision-b", TENANT)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(BPMN_WITH_TWO_FORMS_TWO_DECISIONS, TENANT))
                .isInstanceOf(DanglingReferenceException.class)
                .satisfies(e -> {
                    DanglingReferenceException ex = (DanglingReferenceException) e;
                    assertThat(ex.getMissingForms()).containsExactlyInAnyOrder("form-a", "form-b");
                    assertThat(ex.getMissingDecisions()).containsExactly("decision-a");
                });
    }

    @Test
    @DisplayName("does not throw for a BPMN with no form or decision refs")
    void noRefs_noException() {
        String bpmnNoRefs = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="simple">
                    <userTask id="t1"/>
                  </process>
                </definitions>
                """;

        assertThatCode(() -> validator.validate(bpmnNoRefs, TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("strips @version suffix from form keys before checking existence")
    void stripsVersionSuffix_fromFormKey() {
        String bpmnPinned = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="p1">
                    <startEvent id="s" flowable:formKey="my-form@3"/>
                  </process>
                </definitions>
                """;
        // The base key "my-form" must exist, not the pinned "my-form@3".
        when(formSchemaService.formExistsActiveVersion("my-form", TENANT)).thenReturn(true);

        assertThatCode(() -> validator.validate(bpmnPinned, TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reports archived-only form as missing — formExistsActiveVersion=false is not enough")
    void archivedOnlyForm_reportedAsMissing() {
        // All versions of "my-form" exist but none is active (archived-only).
        when(formSchemaService.formExistsActiveVersion("my-form", TENANT)).thenReturn(false);
        when(dmnDecisionService.decisionExists("my-decision", TENANT)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(BPMN_WITH_FORM_AND_DECISION, TENANT))
                .isInstanceOf(DanglingReferenceException.class)
                .satisfies(e -> {
                    DanglingReferenceException ex = (DanglingReferenceException) e;
                    assertThat(ex.getMissingForms()).containsExactly("my-form");
                    assertThat(ex.getMissingDecisions()).isEmpty();
                });
    }
}
