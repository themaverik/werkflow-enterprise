package com.werkflow.engine.workflow;

import com.werkflow.engine.config.flowable.WerkflowDeadExtensionAttrValidator;
import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link WerkflowDeadExtensionAttrValidator} rejects the F-EV-2 dead-attr class at DEPLOY
 * time via the real Flowable 7.2 parser, using the production-faithful {@code WerkflowTestProcessEngine}.
 *
 * <p>Also verifies that {@code leave-request.bpmn20.xml} (fixed XSD ordering: documentation before
 * extensionElements at the process level) deploys cleanly in an engine with
 * {@code enableSafeBpmnXml=true}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WerkflowDeadExtensionAttrValidator — F-EV-2 dead attrs rejected at deploy")
class DeadExtensionAttrValidatorTest {

    /** Minimal BPMN with {@code flowable:signalName} on a signal catch event — should be rejected. */
    private static final String DEAD_SIGNAL_NAME = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dead-signal-name" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
                <intermediateCatchEvent id="c" flowable:signalName="mySig">
                  <signalEventDefinition signalRef="sigDef"/>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
                <endEvent id="e"/>
              </process>
              <signal id="sigDef" name="mySig"/>
            </definitions>
            """;

    /** Minimal BPMN with {@code flowable:correlationKey} on a message catch event. */
    private static final String DEAD_CORRELATION_KEY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dead-correlation-key" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
                <intermediateCatchEvent id="c" flowable:correlationKey="myKey">
                  <messageEventDefinition messageRef="msgDef"/>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
                <endEvent id="e"/>
              </process>
              <message id="msgDef" name="myMessage"/>
            </definitions>
            """;

    /** Minimal BPMN with {@code flowable:webhookConnector} on a signal event. */
    private static final String DEAD_WEBHOOK_CONNECTOR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dead-webhook-connector" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
                <intermediateCatchEvent id="c" flowable:webhookConnector="my-connector">
                  <signalEventDefinition signalRef="sigDef"/>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
                <endEvent id="e"/>
              </process>
              <signal id="sigDef" name="mySig"/>
            </definitions>
            """;

    /** Minimal BPMN with {@code flowable:correlationExpression} on a message event. */
    private static final String DEAD_CORRELATION_EXPRESSION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dead-correlation-expression" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
                <intermediateCatchEvent id="c" flowable:correlationExpression="${orderId}">
                  <messageEventDefinition messageRef="msgDef"/>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
                <endEvent id="e"/>
              </process>
              <message id="msgDef" name="myMessage"/>
            </definitions>
            """;

    /** Well-formed signal catch event using the standard {@code signalRef} — must deploy cleanly. */
    private static final String VALID_SIGNAL_CATCH = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="valid-signal-catch" isExecutable="true">
                <startEvent id="s"/>
                <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
                <intermediateCatchEvent id="c">
                  <signalEventDefinition signalRef="sigDef"/>
                </intermediateCatchEvent>
                <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
                <endEvent id="e"/>
              </process>
              <signal id="sigDef" name="mySig"/>
            </definitions>
            """;

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;

    @BeforeAll
    void bootEngine() {
        testEngine = WerkflowTestProcessEngine.build("deadExtensionAttr");
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    private void deploy(String name, String xml) {
        repositoryService.createDeployment()
                .addString(name + ".bpmn20.xml", xml)
                .name(name)
                .deploy();
    }

    @Test
    @DisplayName("flowable:signalName is rejected with WERKFLOW_DEAD_EXTENSION_ATTR")
    void deadSignalName_rejected() {
        assertThatThrownBy(() -> deploy("dead-signal-name", DEAD_SIGNAL_NAME))
                .hasMessageContaining(WerkflowDeadExtensionAttrValidator.WERKFLOW_DEAD_EXTENSION_ATTR);
    }

    @Test
    @DisplayName("flowable:correlationKey is rejected with WERKFLOW_DEAD_EXTENSION_ATTR")
    void deadCorrelationKey_rejected() {
        assertThatThrownBy(() -> deploy("dead-correlation-key", DEAD_CORRELATION_KEY))
                .hasMessageContaining(WerkflowDeadExtensionAttrValidator.WERKFLOW_DEAD_EXTENSION_ATTR);
    }

    @Test
    @DisplayName("flowable:webhookConnector is rejected with WERKFLOW_DEAD_EXTENSION_ATTR")
    void deadWebhookConnector_rejected() {
        assertThatThrownBy(() -> deploy("dead-webhook-connector", DEAD_WEBHOOK_CONNECTOR))
                .hasMessageContaining(WerkflowDeadExtensionAttrValidator.WERKFLOW_DEAD_EXTENSION_ATTR);
    }

    @Test
    @DisplayName("flowable:correlationExpression is rejected with WERKFLOW_DEAD_EXTENSION_ATTR")
    void deadCorrelationExpression_rejected() {
        assertThatThrownBy(() -> deploy("dead-correlation-expression", DEAD_CORRELATION_EXPRESSION))
                .hasMessageContaining(WerkflowDeadExtensionAttrValidator.WERKFLOW_DEAD_EXTENSION_ATTR);
    }

    @Test
    @DisplayName("valid signal catch (no dead attrs) deploys cleanly")
    void validSignalCatch_deploys() {
        assertThatCode(() -> deploy("valid-signal-catch", VALID_SIGNAL_CATCH))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("leave-request.bpmn20.xml deploys cleanly after XSD ordering fix")
    void leaveRequest_deploysCleanly() {
        assertThatCode(() ->
                repositoryService.createDeployment()
                        .addClasspathResource("processes/examples/leave-request.bpmn20.xml")
                        .name("leave-request-xsd-fix")
                        .deploy()
        ).doesNotThrowAnyException();
    }
}
