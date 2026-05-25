package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.service.FormSchemaService;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.util.io.StringStreamSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BpmnFormKeyPinner} — the ADR-026 P2/F1 form-version pin applied
 * to BPMN at bundle-deploy time.
 */
class BpmnFormKeyPinnerTest {

    private FormSchemaService formSchemaService;
    private BpmnFormKeyPinner pinner;

    @BeforeEach
    void setUp() {
        formSchemaService = mock(FormSchemaService.class);
        pinner = new BpmnFormKeyPinner(formSchemaService);
    }

    private FormSchema schemaAtVersion(int version) {
        FormSchema schema = mock(FormSchema.class);
        when(schema.getVersion()).thenReturn(version);
        return schema;
    }

    @Test
    @DisplayName("pins static formKeys on start events and user tasks to their current version")
    void pinsStaticFormKeys() {
        FormSchema capex = schemaAtVersion(3);
        FormSchema onboarding = schemaAtVersion(5);
        when(formSchemaService.loadFormSchema("capex-request-form")).thenReturn(capex);
        when(formSchemaService.loadFormSchema("onboarding-checklist-form")).thenReturn(onboarding);

        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="p1">
                    <startEvent id="s" flowable:formKey="capex-request-form"/>
                    <userTask id="u1" flowable:formKey="onboarding-checklist-form"/>
                  </process>
                </definitions>
                """;

        String result = pinner.pinFormKeys(xml);

        assertThat(result).contains("capex-request-form@3");
        assertThat(result).contains("onboarding-checklist-form@5");
    }

    @Test
    @DisplayName("leaves EL expressions, already-pinned keys, and unprovisioned keys unchanged")
    void leavesNonPinnableKeysUnchanged() {
        when(formSchemaService.loadFormSchema("missing-form"))
                .thenThrow(new FormNotFoundException("missing-form"));

        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="p1">
                    <userTask id="u1" flowable:formKey="${dynamicForm}"/>
                    <userTask id="u2" flowable:formKey="already-pinned@2"/>
                    <userTask id="u3" flowable:formKey="missing-form"/>
                  </process>
                </definitions>
                """;

        String result = pinner.pinFormKeys(xml);

        assertThat(result).contains("${dynamicForm}");
        assertThat(result).contains("already-pinned@2");
        assertThat(result).contains("missing-form");
        assertThat(result).doesNotContain("missing-form@");
    }

    @Test
    @DisplayName("returns the original XML string unchanged when nothing is pinnable")
    void returnsOriginalWhenNothingToPin() {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="p1">
                    <userTask id="u1"/>
                  </process>
                </definitions>
                """;

        String result = pinner.pinFormKeys(xml);

        assertThat(result).isEqualTo(xml);
    }

    @Test
    @DisplayName("pinned output of a real multi-namespace BPMN still parses via Flowable's converter")
    void realFixtureRoundtripsThroughFlowableConverter() throws Exception {
        FormSchema v1 = schemaAtVersion(1);
        when(formSchemaService.loadFormSchema(anyString())).thenReturn(v1);

        byte[] bytes;
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("processes/examples/capex-approval-process.bpmn20.xml")) {
            assertThat(in).as("fixture present on test classpath").isNotNull();
            bytes = in.readAllBytes();
        }
        String original = new String(bytes, StandardCharsets.UTF_8);

        String pinned = pinner.pinFormKeys(original);

        assertThat(pinned).as("at least one formKey was pinned").contains("@1");
        // The re-serialised XML must still be valid BPMN that Flowable can deploy.
        BpmnModel model = new BpmnXMLConverter()
                .convertToBpmnModel(new StringStreamSource(pinned), false, false);
        assertThat(model.getProcesses()).isNotEmpty();
    }
}
