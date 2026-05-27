package com.werkflow.engine.workflow;

import com.werkflow.engine.config.flowable.WerkflowLinkEventValidator;
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
 * Proves that {@link WerkflowLinkEventValidator} rejects link catch and link throw events at
 * deploy time, against the real Flowable 7.2 BPMN parser, and that a clean process deploys.
 *
 * <p>Uses {@link WerkflowTestProcessEngine} so the same parse handlers and the full Werkflow
 * validator family (including {@link WerkflowLinkEventValidator}) are applied — identical to
 * production deploy-time behaviour. Mirrors {@link DeadConfigValidatorDeploymentTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WerkflowLinkEventValidator rejects link events at deploy time (real parser)")
class WerkflowLinkEventValidatorDeploymentTest {

    /** A link intermediate catch event. Flowable drops the definition; our validator fires first. */
    private static final String LINK_CATCH_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="link-catch-reject" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="lc"/>
            <intermediateCatchEvent id="lc" name="LinkCatch">
              <linkEventDefinition id="ld1" name="MyLink"/>
            </intermediateCatchEvent>
            <sequenceFlow id="f2" sourceRef="lc" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /** A link intermediate throw event. Flowable drops the definition silently; our validator must catch it. */
    private static final String LINK_THROW_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="link-throw-reject" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="lt"/>
            <intermediateThrowEvent id="lt" name="LinkThrow">
              <linkEventDefinition id="ld2" name="MyLink"/>
            </intermediateThrowEvent>
            <sequenceFlow id="f2" sourceRef="lt" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    /**
     * A clean process with a timer intermediate catch event (no link events) — must deploy without error.
     * Uses a supported event type to confirm the validator does not over-reject.
     */
    private static final String CLEAN_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <process id="link-clean-deploy" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="task"/>
            <userTask id="task" name="Review"/>
            <sequenceFlow id="f2" sourceRef="task" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;

    @BeforeAll
    void bootEngine() {
        testEngine = WerkflowTestProcessEngine.build("linkEventValidatorDeploy");
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    private void deploy(String name, String xml) {
        repositoryService.createDeployment().addString(name + ".bpmn20.xml", xml).name(name).deploy();
    }

    @Test
    @DisplayName("link catch event is rejected with WERKFLOW_LINK_EVENT_UNSUPPORTED")
    void linkCatchEvent_rejected() {
        assertThatThrownBy(() -> deploy("link-catch-reject", LINK_CATCH_BPMN))
                .hasMessageContaining(WerkflowLinkEventValidator.WERKFLOW_LINK_EVENT_UNSUPPORTED);
    }

    @Test
    @DisplayName("link throw event is rejected with WERKFLOW_LINK_EVENT_UNSUPPORTED")
    void linkThrowEvent_rejected() {
        assertThatThrownBy(() -> deploy("link-throw-reject", LINK_THROW_BPMN))
                .hasMessageContaining(WerkflowLinkEventValidator.WERKFLOW_LINK_EVENT_UNSUPPORTED);
    }

    @Test
    @DisplayName("process with no link events deploys cleanly")
    void cleanProcess_deploys() {
        assertThatCode(() -> deploy("link-clean-deploy", CLEAN_BPMN)).doesNotThrowAnyException();
    }
}
