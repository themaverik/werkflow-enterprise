package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.WerkflowProcessTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capstone flow test: form variables feed a DMN evaluation whose output drives
 * gateway routing — exercising the full BPMN + DMN + candidateGroup stack under
 * the Werkflow harness (ADR-028 Phase 4).
 */
@Tag("flow")
@DisplayName("Procurement routing flow — integrated BPMN+DMN capstone (ADR-028 Phase 4)")
class ProcurementRoutingFlowTest extends WerkflowProcessTest {

    @BeforeAll
    void setup() {
        startEngine("procurementFlow");
        dsl.deploy("dmn-examples/procurement-matrix.dmn");
        dsl.deploy("flows/procurement-routing-flow.bpmn20.xml");
    }

    @Test
    @DisplayName("small amount routes to directApproval task")
    void smallAmount_routesToDirectApproval() {
        dsl.start("procurement-routing-flow", Map.of("amount", 10000, "category", "SUPPLIES"))
           .assertWaitingAt("directApproval");
    }

    @Test
    @DisplayName("mid-range amount routes to managerApproval task")
    void midAmount_routesToManagerApproval() {
        dsl.start("procurement-routing-flow", Map.of("amount", 75000, "category", "OFFICE"))
           .assertWaitingAt("managerApproval");
    }

    @Test
    @DisplayName("high amount routes to committeeApproval task")
    void highAmount_routesToCommitteeApproval() {
        dsl.start("procurement-routing-flow", Map.of("amount", 250000, "category", "LOGISTICS"))
           .assertWaitingAt("committeeApproval");
    }

    @Test
    @DisplayName("IT category > 500k routes to boardApproval task")
    void itHighValue_routesToBoardApproval() {
        dsl.start("procurement-routing-flow", Map.of("amount", 600000, "category", "IT"))
           .assertWaitingAt("boardApproval");
    }

    @Test
    @DisplayName("direct purchase path completes after task completion")
    void directPath_completesAfterTaskComplete() {
        dsl.start("procurement-routing-flow", Map.of("amount", 10000, "category", "SUPPLIES"))
           .assertWaitingAt("directApproval")
           .completeTask("directApproval", Map.of())
           .assertCompleted();
    }

    @Test
    @DisplayName("DMN output variable procurementPath is set as process variable")
    void dmnOutput_procurementPath_isSetOnProcess() {
        dsl.start("procurement-routing-flow", Map.of("amount", 10000, "category", "SUPPLIES"))
           .assertVariable("procurementPath", "DIRECT_PURCHASE")
           .assertVariable("requiresCommittee", false);
    }
}
