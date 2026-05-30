package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.DmnTestRunner.DecisionRunner;
import com.werkflow.engine.testsupport.WerkflowProcessTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fragment")
@DisplayName("Shipped DMN decision tables — harness isolation (ADR-028 Phase 2)")
class DmnDecisionTest extends WerkflowProcessTest {

    private DecisionRunner procurement;
    private DecisionRunner leave;

    @BeforeAll
    void setup() {
        startEngine("dmnDecision");
        procurement = dmnRunner.deploy("dmn/procurement-matrix.dmn", "procurement_matrix");
        leave = dmnRunner.deploy("dmn/leave-approval.dmn", "leave_approval");
    }

    // ---- procurement-matrix ----

    @Test
    @DisplayName("procurement-matrix: amount <= 50k routes to direct purchase, no committee")
    void procurementMatrix_smallAmount_directPurchase() {
        var result = procurement.outputs(
            Map.of("amount", 10000, "category", "SUPPLIES"),
            "procurementPath", "requiresCommittee");
        assertThat(result.get("procurementPath")).isEqualTo("DIRECT_PURCHASE");
        assertThat(result.get("requiresCommittee")).isEqualTo(false);
    }

    @Test
    @DisplayName("procurement-matrix: amount > 200k routes to committee review, requires committee")
    void procurementMatrix_highAmount_committeeReview() {
        var result = procurement.outputs(
            Map.of("amount", 250000, "category", "LOGISTICS"),
            "procurementPath", "requiresCommittee");
        assertThat(result.get("procurementPath")).isEqualTo("COMMITTEE_REVIEW");
        assertThat(result.get("requiresCommittee")).isEqualTo(true);
    }

    @Test
    @DisplayName("procurement-matrix: IT > 500k routes to board approval")
    void procurementMatrix_itBoard_boardApproval() {
        assertThat(procurement.output("procurementPath", Map.of("amount", 600000, "category", "IT")))
            .isEqualTo("BOARD_APPROVAL");
    }

    // ---- leave-approval ----

    @Test
    @DisplayName("leave-approval: 1–3 casual days auto-approved, no approver required")
    void leaveApproval_shortCasual_autoApproved() {
        var result = leave.outputs(
            Map.of("leaveDays", 2, "leaveType", "CASUAL"),
            "approvalRequired", "approverRole");
        assertThat(result.get("approvalRequired")).isEqualTo(false);
        assertThat(result.get("approverRole")).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("leave-approval: > 10 days annual requires HR manager")
    void leaveApproval_longAnnual_hrManager() {
        assertThat(leave.output("approverRole", Map.of("leaveDays", 15, "leaveType", "ANNUAL")))
            .isEqualTo("HR_MANAGER");
    }

    @Test
    @DisplayName("leave-approval: medical (any duration) requires HR manager")
    void leaveApproval_medical_hrManager() {
        assertThat(leave.output("approverRole", Map.of("leaveDays", 1, "leaveType", "MEDICAL")))
            .isEqualTo("HR_MANAGER");
    }
}
