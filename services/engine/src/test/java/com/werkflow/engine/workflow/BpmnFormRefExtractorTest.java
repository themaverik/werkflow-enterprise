package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.FormSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BpmnFormRefExtractor}.
 */
@DisplayName("BpmnFormRefExtractor — unit")
class BpmnFormRefExtractorTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final String CAPEX_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn">
          <process id="capex-approval-process" isExecutable="true">
            <startEvent id="start" flowable:formKey="capex-request-form"/>
            <userTask id="approval" flowable:actionType="HUMAN_APPROVAL"
                      flowable:formKey="capex-approval-form"/>
            <userTask id="review" flowable:formKey="capex-review-form"/>
          </process>
        </definitions>
        """;

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("start event formKey → TASK_FORM")
    void startEvent_returnsTaskForm() {
        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(CAPEX_BPMN));

        assertThat(refs).containsEntry("capex-request-form", FormSchema.FormType.TASK_FORM);
    }

    @Test
    @DisplayName("HUMAN_APPROVAL userTask formKey → APPROVAL")
    void humanApprovalTask_returnsApproval() {
        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(CAPEX_BPMN));

        assertThat(refs).containsEntry("capex-approval-form", FormSchema.FormType.APPROVAL);
    }

    @Test
    @DisplayName("plain userTask formKey (no actionType) → TASK_FORM")
    void plainUserTask_returnsTaskForm() {
        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(CAPEX_BPMN));

        assertThat(refs).containsEntry("capex-review-form", FormSchema.FormType.TASK_FORM);
    }

    @Test
    @DisplayName("first occurrence wins — start event key preserved when same key on userTask")
    void firstOccurrenceWins_startEventBeforeUserTask() {
        String bpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="test">
              <process id="p" isExecutable="true">
                <startEvent id="s" flowable:formKey="shared-form"/>
                <userTask id="u" flowable:actionType="HUMAN_APPROVAL"
                          flowable:formKey="shared-form"/>
              </process>
            </definitions>""";

        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(bpmn));

        // Start event is processed first → TASK_FORM wins
        assertThat(refs).containsEntry("shared-form", FormSchema.FormType.TASK_FORM);
        assertThat(refs).hasSize(1);
    }

    @Test
    @DisplayName("blank formKey and missing formKey are skipped")
    void blankAndMissingFormKey_skipped() {
        String bpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="test">
              <process id="p" isExecutable="true">
                <startEvent id="s" flowable:formKey="   "/>
                <userTask id="u1"/>
                <userTask id="u2" flowable:formKey=""/>
              </process>
            </definitions>""";

        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(bpmn));

        assertThat(refs).isEmpty();
    }

    @Test
    @DisplayName("returned map is immutable")
    void returnedMap_isImmutable() {
        Map<String, FormSchema.FormType> refs = BpmnFormRefExtractor.extractFormRefs(parse(CAPEX_BPMN));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> refs.put("extra-key", FormSchema.FormType.TASK_FORM))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
