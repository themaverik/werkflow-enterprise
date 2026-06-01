package com.werkflow.engine.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnIndicatorScannerTest {

    private final BpmnIndicatorScanner scanner = new BpmnIndicatorScanner();

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static String bpmn(String body) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
            %s
              </process>
            </definitions>
            """.formatted(body);
    }

    // -----------------------------------------------------------------------
    // base cases
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("pure user-task process → (false, false)")
    void pureUserTask_noIndicators() {
        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn("<userTask id=\"t1\"/>"));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // DMN detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("serviceTask with flowable:type='dmn' → hasDmn=true, hasConnector=false")
    void dmnServiceTask_onlyDmn() {
        String body = "<serviceTask id=\"d1\" flowable:type=\"dmn\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasDmn()).isTrue();
        assertThat(ind.hasConnector()).isFalse();
    }

    @Test
    @DisplayName("businessRuleTask without a DMN serviceTask → hasDmn=false (dead-config guard)")
    void businessRuleTask_doesNotTriggerDmn() {
        String body = "<businessRuleTask id=\"brt1\" flowable:decisionTableReferenceKey=\"some_decision\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Connector detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("serviceTask with restConnectorDelegate → hasConnector=true, hasDmn=false")
    void restConnectorDelegate_onlyConnector() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${restConnectorDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isTrue();
    }

    @Test
    @DisplayName("connectorWebhookDelegate → hasConnector=true")
    void connectorWebhookDelegate_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${connectorWebhookDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    @Test
    @DisplayName("connectorCallDelegate (legacy) → hasConnector=true")
    void connectorCallDelegate_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${connectorCallDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Combined
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("both DMN serviceTask and connector serviceTask → (true, true)")
    void bothPresent() {
        String body = """
            <serviceTask id="d1" flowable:type="dmn"/>
            <serviceTask id="c1" flowable:delegateExpression="${restConnectorDelegate}"/>
            """;

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasDmn()).isTrue();
        assertThat(ind.hasConnector()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Whitespace tolerance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("whitespace inside ${ restConnectorDelegate } is tolerated")
    void whitespace_insideExpression_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${ restConnectorDelegate }\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    // -----------------------------------------------------------------------
    // False-positive guard
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("delegateExpression with substring-only match does NOT trigger hasConnector")
    void substringDelegate_doesNotFalsePositive() {
        // Bean name contains "restConnectorDelegate" but is a different bean
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${myRestConnectorDelegateWrapper}\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Error path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("malformed XML → IllegalArgumentException")
    void malformedXml_throws() {
        assertThatThrownBy(() -> scanner.scan("<definitions><process"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
