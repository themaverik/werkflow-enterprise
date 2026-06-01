package com.werkflow.engine.workflow;

import com.werkflow.engine.action.ConnectorDelegateBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BpmnIndicatorScannerTest {

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a scanner using the real-world connector bean names present in the
     * engine Spring context (including the restConnectorDelegate alias).
     */
    private static BpmnIndicatorScanner newScanner(String... beanNames) {
        Map<String, ConnectorDelegateBase> map = new HashMap<>();
        for (String name : beanNames) {
            map.put(name, mock(ConnectorDelegateBase.class));
        }
        return new BpmnIndicatorScanner(map);
    }

    /** Scanner pre-loaded with all production connector bean names + the alias. */
    private static BpmnIndicatorScanner defaultScanner() {
        return newScanner(
            "externalApiCallDelegate",
            "restConnectorDelegate",       // alias from RestConnectorDelegateAlias
            "databaseConnectorDelegate",
            "connectorWebhookDelegate"
        );
    }

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
    @DisplayName("pure user-task process → (false, false, false)")
    void pureUserTask_noIndicators() {
        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn("<userTask id=\"t1\"/>"));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isFalse();
        assertThat(ind.hasNotification()).isFalse();
    }

    // -----------------------------------------------------------------------
    // DMN detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("serviceTask with flowable:type='dmn' → hasDmn=true, hasConnector=false")
    void dmnServiceTask_onlyDmn() {
        String body = "<serviceTask id=\"d1\" flowable:type=\"dmn\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasDmn()).isTrue();
        assertThat(ind.hasConnector()).isFalse();
        assertThat(ind.hasNotification()).isFalse();
    }

    @Test
    @DisplayName("businessRuleTask without a DMN serviceTask → hasDmn=false (dead-config guard)")
    void businessRuleTask_doesNotTriggerDmn() {
        String body = "<businessRuleTask id=\"brt1\" flowable:decisionTableReferenceKey=\"some_decision\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Connector detection — all real bean names
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("externalApiCallDelegate → hasConnector=true, hasDmn=false, hasNotification=false")
    void externalApiCallDelegate_onlyConnector() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${externalApiCallDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasNotification()).isFalse();
    }

    @Test
    @DisplayName("restConnectorDelegate alias → hasConnector=true")
    void restConnectorDelegate_aliasMatches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${restConnectorDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    @Test
    @DisplayName("databaseConnectorDelegate → hasConnector=true")
    void databaseConnectorDelegate_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${databaseConnectorDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    @Test
    @DisplayName("connectorWebhookDelegate → hasConnector=true")
    void connectorWebhookDelegate_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${connectorWebhookDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Notification detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("notificationDelegate on serviceTask → hasNotification=true, hasConnector=false")
    void notificationDelegate_serviceTask_hasNotification() {
        String body = "<serviceTask id=\"n1\" flowable:delegateExpression=\"${notificationDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasNotification()).isTrue();
        assertThat(ind.hasConnector()).isFalse();
        assertThat(ind.hasDmn()).isFalse();
    }

    @Test
    @DisplayName("notificationDelegate on sendTask → hasNotification=true, hasConnector=false")
    void notificationDelegate_sendTask_hasNotification() {
        String body = "<sendTask id=\"s1\" flowable:delegateExpression=\"${notificationDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasNotification()).isTrue();
        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Combined
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("both DMN serviceTask and connector serviceTask → (true, true, false)")
    void bothDmnAndConnector() {
        String body = """
            <serviceTask id="d1" flowable:type="dmn"/>
            <serviceTask id="c1" flowable:delegateExpression="${restConnectorDelegate}"/>
            """;

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasDmn()).isTrue();
        assertThat(ind.hasConnector()).isTrue();
        assertThat(ind.hasNotification()).isFalse();
    }

    @Test
    @DisplayName("connector + notification → (false, true, true)")
    void connectorAndNotification() {
        String body = """
            <serviceTask id="c1" flowable:delegateExpression="${externalApiCallDelegate}"/>
            <sendTask id="s1" flowable:delegateExpression="${notificationDelegate}"/>
            """;

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasDmn()).isFalse();
        assertThat(ind.hasConnector()).isTrue();
        assertThat(ind.hasNotification()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Whitespace tolerance
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("whitespace inside ${ restConnectorDelegate } is tolerated")
    void whitespace_insideExpression_matches() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${ restConnectorDelegate }\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isTrue();
    }

    // -----------------------------------------------------------------------
    // False-positive guards
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("${myExternalApiCallDelegateWrapper} does NOT trigger hasConnector")
    void substringConnectorDelegate_doesNotFalsePositive() {
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${myExternalApiCallDelegateWrapper}\"/>";

        BpmnIndicatorScanner.Indicators ind = defaultScanner().scan(bpmn(body));

        assertThat(ind.hasConnector()).isFalse();
    }

    @Test
    @DisplayName("bean not in the scanner's set → hasConnector=false even if similar name")
    void unknownBean_notInSet_doesNotMatch() {
        // Scanner built with only externalApiCallDelegate — restConnectorDelegate is absent
        BpmnIndicatorScanner scanner = newScanner("externalApiCallDelegate");
        String body = "<serviceTask id=\"c1\" flowable:delegateExpression=\"${restConnectorDelegate}\"/>";

        BpmnIndicatorScanner.Indicators ind = scanner.scan(bpmn(body));

        assertThat(ind.hasConnector()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Error path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("malformed XML → IllegalArgumentException")
    void malformedXml_throws() {
        assertThatThrownBy(() -> defaultScanner().scan("<definitions><process"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
