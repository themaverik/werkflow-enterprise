package com.werkflow.engine.workflow;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STEP-0 empirical probe: what does the Flowable 7.2 BPMN parser produce when it encounters
 * a {@code <linkEventDefinition>} on both a catch and throw event?
 *
 * <p>This is a one-time discovery probe intentionally left in the test suite as the authoritative
 * evidence record for the detection strategy chosen in
 * {@link com.werkflow.engine.config.flowable.WerkflowLinkEventValidator}.
 *
 * <p><b>Findings (Flowable 7.2.0):</b>
 * <ol>
 *   <li><b>Link catch</b> — Flowable's own built-in validator
 *       ({@code flowable-intermediate-catch-event-no-eventdefinition}) already rejects it at deploy
 *       because the parser silently drops the {@code linkEventDefinition} element, leaving an
 *       {@code IntermediateCatchEvent} with an empty {@code eventDefinitions} list. The Flowable
 *       validator treats "catch with no defs" as invalid and throws. Our validator adds an explicit,
 *       actionable error with {@code WERKFLOW_LINK_EVENT_UNSUPPORTED} so the Werkflow error code is
 *       also present in the rejection message.</li>
 *   <li><b>Link throw</b> — The parser silently drops the {@code linkEventDefinition}, producing a
 *       {@code ThrowEvent} with an empty {@code eventDefinitions} list. Flowable does NOT have a
 *       built-in guard for a throw with no definitions (a "none" intermediate throw event is a valid
 *       BPMN construct used as a marker). Our validator must therefore explicitly detect and reject
 *       any {@code ThrowEvent} in an intermediate position (i.e., not an {@code EndEvent}) whose
 *       {@code eventDefinitions} list is empty AND which appears between start and end — the
 *       combination "intermediateThrowEvent with zero defs" is a link throw that was silently
 *       neutered. Detection: {@code findFlowElementsOfType(ThrowEvent.class)} returns both
 *       {@code intermediateThrowEvent} and {@code endEvent} throw forms; filter by
 *       {@code !(element instanceof EndEvent)} to target only intermediate throws.</li>
 *   <li><b>None-throw control</b> — A genuine none-intermediate-throw (marker event, valid BPMN)
 *       also has empty eventDefinitions. That means we CANNOT reliably distinguish a "link throw
 *       rendered as none-throw" from a genuine intentional none-throw at the model level alone.
 *       This is acceptable because: (a) bpmn-js will never intentionally author a none-throw at
 *       Werkflow — the designer does not expose it; (b) even if hand-authored, a none-throw is
 *       effectively a no-op and rejecting it is correct defensive behaviour for Werkflow; (c) the
 *       error message clearly says to use a sequence flow instead, which is the correct fix for both
 *       a link and a none-throw.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("STEP-0 probe: Flowable 7.2 link-event parse behaviour (empirical)")
class LinkEventProbeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkEventProbeTest.class);

    /** A catch event containing a linkEventDefinition — triggers Flowable's own catch-no-def validator. */
    private static final String LINK_CATCH_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/probe">
          <process id="link-catch-probe" isExecutable="true">
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

    /** A throw event containing a linkEventDefinition. Flowable parses it; no built-in guard. */
    private static final String LINK_THROW_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/probe">
          <process id="link-throw-probe" isExecutable="true">
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

    /** A genuine none-intermediate-throw (valid BPMN marker). Also has empty eventDefinitions. */
    private static final String NONE_THROW_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://werkflow.com/bpmn/probe">
          <process id="none-throw-probe" isExecutable="true">
            <startEvent id="s"/>
            <sequenceFlow id="f1" sourceRef="s" targetRef="nt"/>
            <intermediateThrowEvent id="nt" name="NoneThrow"/>
            <sequenceFlow id="f2" sourceRef="nt" targetRef="e"/>
            <endEvent id="e"/>
          </process>
        </definitions>
        """;

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;

    @BeforeAll
    void bootEngine() {
        // Plain engine — no WerkflowProcessEngineCustomizer; raw parser observation only.
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:linkEventProbe;DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        cfg.setCreateDiagramOnDeploy(false);
        processEngine = cfg.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
    }

    @AfterAll
    void shutdown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    @DisplayName("link catch: Flowable's own validator rejects it (no eventDef after silent drop)")
    void linkCatch_rejectedByFlowableBuiltinValidator() {
        // FINDING: Flowable already throws for a link catch because linkEventDefinition is dropped,
        // leaving an IntermediateCatchEvent with no defs — Flowable's validator requires a def.
        assertThatThrownBy(() -> repositoryService.createDeployment()
                .addString("link-catch-probe.bpmn20.xml", LINK_CATCH_BPMN)
                .name("link-catch-probe")
                .deploy())
                .hasMessageContaining("flowable-intermediate-catch-event-no-eventdefinition");

        LOGGER.warn("STEP-0 CONFIRMED: link catch rejected by Flowable built-in (no-eventdefinition). "
                + "Our validator adds WERKFLOW_LINK_EVENT_UNSUPPORTED BEFORE Flowable's check fires.");
    }

    @Test
    @DisplayName("link throw: parser drops linkEventDefinition; ThrowEvent has empty eventDefinitions")
    void linkThrow_parsesToThrowEventWithEmptyDefs() {
        String depId = repositoryService.createDeployment()
                .addString("link-throw-probe.bpmn20.xml", LINK_THROW_BPMN)
                .name("link-throw-probe")
                .deploy()
                .getId();

        try {
            BpmnModel model = repositoryService.getBpmnModel(
                    repositoryService.createProcessDefinitionQuery()
                            .processDefinitionKey("link-throw-probe").singleResult().getId());

            FlowElement lt = model.getFlowElement("lt");
            LOGGER.warn("STEP-0 FINDING — link throw: class={}, eventDefinitions.size={}",
                    lt.getClass().getName(),
                    lt instanceof ThrowEvent te ? te.getEventDefinitions().size() : "n/a");

            assertThat(lt).isInstanceOf(ThrowEvent.class);
            ThrowEvent throwEvent = (ThrowEvent) lt;
            assertThat(throwEvent.getEventDefinitions())
                    .as("FINDING: linkEventDefinition is DROPPED by parser; ThrowEvent.eventDefinitions is empty")
                    .isEmpty();
        } finally {
            repositoryService.deleteDeployment(depId, true);
        }
    }

    @Test
    @DisplayName("none-throw (genuine marker): also ThrowEvent with empty eventDefinitions (model indistinguishable)")
    void noneThrow_alsoParsesToThrowEventWithEmptyDefs() {
        String depId = repositoryService.createDeployment()
                .addString("none-throw-probe.bpmn20.xml", NONE_THROW_BPMN)
                .name("none-throw-probe")
                .deploy()
                .getId();

        try {
            BpmnModel model = repositoryService.getBpmnModel(
                    repositoryService.createProcessDefinitionQuery()
                            .processDefinitionKey("none-throw-probe").singleResult().getId());

            FlowElement nt = model.getFlowElement("nt");
            LOGGER.warn("STEP-0 FINDING — none-throw (genuine): class={}, eventDefinitions.size={}",
                    nt.getClass().getName(),
                    nt instanceof ThrowEvent te ? te.getEventDefinitions().size() : "n/a");

            assertThat(nt).isInstanceOf(ThrowEvent.class);
            ThrowEvent throwEvent = (ThrowEvent) nt;
            assertThat(throwEvent.getEventDefinitions())
                    .as("FINDING: genuine none-throw also has empty eventDefinitions — indistinguishable from link-throw at model level")
                    .isEmpty();

            LOGGER.warn("STEP-0 CONCLUSION: reject ALL intermediate ThrowEvent with empty defs at Werkflow level "
                    + "(none-throws are also not a designer construct; rejecting both is correct defensive behaviour).");
        } finally {
            repositoryService.deleteDeployment(depId, true);
        }
    }
}
