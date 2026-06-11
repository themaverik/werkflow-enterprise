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

    private DecisionRunner leave;

    @BeforeAll
    void setup() {
        startEngine("dmnDecision");
        leave = dmnRunner.deploy("examples/tenants/default/dmn/leave-approval.dmn", "leave_approval");
    }

    // ---- leave-approval ----

    @Test
    @DisplayName("leave-approval: short annual leave auto-approved, no approver required")
    void leaveApproval_shortAnnual_autoApproved() {
        var result = leave.outputs(
            Map.of("leaveDays", 2, "leaveType", "annual"),
            "approvalRequired", "approverRole");
        assertThat(result.get("approvalRequired")).isEqualTo(false);
        assertThat(result.get("approverRole")).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("leave-approval: > 10 days annual requires HR manager")
    void leaveApproval_longAnnual_hrManager() {
        assertThat(leave.output("approverRole", Map.of("leaveDays", 15, "leaveType", "annual")))
            .isEqualTo("HR_MANAGER");
    }

    @Test
    @DisplayName("leave-approval: sick leave (any duration) requires HR manager")
    void leaveApproval_sick_hrManager() {
        assertThat(leave.output("approverRole", Map.of("leaveDays", 1, "leaveType", "sick")))
            .isEqualTo("HR_MANAGER");
    }
}
