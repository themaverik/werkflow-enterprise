package com.werkflow.engine.workflow;

import com.werkflow.engine.testsupport.DmnTestRunner;
import com.werkflow.engine.testsupport.WerkflowProcessTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@code capex-approver-resolution.dmn} — proves each of the three decisions
 * ({@code capex_manager_group}, {@code capex_vp_group}, {@code capex_cfo_group}) resolves the
 * correct DOA group from a department code (ADR-029 Phase 2).
 *
 * <p>Uses {@link DmnTestRunner} to evaluate each decision key in isolation via the production
 * {@code flowable:type="dmn"} path, so the same JUEL evaluation chain fires as in production.
 */
@Tag("flow")
@DisplayName("CapEx approver group DMN — capexOwner resolves to correct DOA group per level")
class CapexApproverGroupTest extends WerkflowProcessTest {

    private DmnTestRunner.DecisionRunner managerGroup;
    private DmnTestRunner.DecisionRunner vpGroup;
    private DmnTestRunner.DecisionRunner cfoGroup;

    @BeforeAll
    void setup() {
        startEngine("capexApproverGroup");
        managerGroup = dmnRunner.deploy("dmn/capex-approver-resolution.dmn", "capex_manager_group");
        vpGroup      = dmnRunner.deploy("dmn/capex-approver-resolution.dmn", "capex_vp_group");
        cfoGroup     = dmnRunner.deploy("dmn/capex-approver-resolution.dmn", "capex_cfo_group");
    }

    // ===== capex_manager_group =====

    @Test
    @DisplayName("FIN owner → manager group resolves to DOA_L2")
    void managerGroup_fin_resolvesDOA_L2() {
        assertThat(managerGroup.output("approverGroup", Map.of("capexOwner", "FIN")))
                .isEqualTo("DOA_L2");
    }

    @Test
    @DisplayName("unknown owner → manager group falls back to DOA_L2")
    void managerGroup_unknown_fallbackDOA_L2() {
        assertThat(managerGroup.output("approverGroup", Map.of("capexOwner", "OPS")))
                .isEqualTo("DOA_L2");
    }

    // ===== capex_vp_group =====

    @Test
    @DisplayName("FIN owner → VP group resolves to DOA_L3")
    void vpGroup_fin_resolvesDOA_L3() {
        assertThat(vpGroup.output("approverGroup", Map.of("capexOwner", "FIN")))
                .isEqualTo("DOA_L3");
    }

    @Test
    @DisplayName("unknown owner → VP group falls back to DOA_L3")
    void vpGroup_unknown_fallbackDOA_L3() {
        assertThat(vpGroup.output("approverGroup", Map.of("capexOwner", "OPS")))
                .isEqualTo("DOA_L3");
    }

    // ===== capex_cfo_group =====

    @Test
    @DisplayName("FIN owner → CFO group resolves to DOA_L4")
    void cfoGroup_fin_resolvesDOA_L4() {
        assertThat(cfoGroup.output("approverGroup", Map.of("capexOwner", "FIN")))
                .isEqualTo("DOA_L4");
    }

    @Test
    @DisplayName("unknown owner → CFO group falls back to DOA_L4")
    void cfoGroup_unknown_fallbackDOA_L4() {
        assertThat(cfoGroup.output("approverGroup", Map.of("capexOwner", "OPS")))
                .isEqualTo("DOA_L4");
    }
}
