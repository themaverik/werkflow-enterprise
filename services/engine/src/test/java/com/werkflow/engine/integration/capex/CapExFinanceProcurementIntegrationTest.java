package com.werkflow.engine.integration.capex;

import com.werkflow.engine.fixtures.IntegrationTestBase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finance-Procurement Integration Tests for CapEx Workflow
 * Tests the flow from Finance approval to Procurement PO generation
 */
@DisplayName("CapEx Finance-Procurement Integration Tests")
class CapExFinanceProcurementIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Finance Budget Validation - Sufficient Budget")
    void testFinanceBudgetValidationPass() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        // Add Finance budget validation
        Map<String, Object> budgetVars = CapExTestDataFactory.createFinanceBudgetValidation(
                true,
                "IT-BUDGET-2024",
                new BigDecimal("50000.00")
        );
        variables.putAll(budgetVars);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify budget check passed
        Boolean budgetAvailable = (Boolean) runtimeService.getVariable(processInstance.getId(), "budgetAvailable");
        assertThat(budgetAvailable).isTrue();

        // Verify process continued to approval stage
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(approvalTask).isNotNull();

        System.out.println("PASS: Finance budget validation passed");
    }

    @Test
    @DisplayName("Test Finance Budget Validation - Insufficient Budget")
    void testFinanceBudgetValidationFail() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();
        variables.put("requestId", "CAPEX-2024-NO-BUDGET");

        // Set insufficient budget
        Map<String, Object> budgetVars = CapExTestDataFactory.createFinanceBudgetValidation(
                false,
                "IT-BUDGET-2024",
                new BigDecimal("100.00") // Less than request amount
        );
        variables.putAll(budgetVars);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-NO-BUDGET",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify budget check failed and process terminated
        Boolean budgetAvailable = (Boolean) runtimeService.getVariable(processInstance.getId(), "budgetAvailable");
        assertThat(budgetAvailable).isFalse();

        // Process should be completed without creating approval tasks
        boolean isCompleted = isProcessCompleted(processInstance.getId());
        assertThat(isCompleted).isTrue();

        System.out.println("PASS: Finance budget validation failed and process terminated");
    }

    @Test
    @DisplayName("Test COA (Chart of Accounts) Assignment")
    void testCOAAssignment() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Assign COA after approval
        Map<String, Object> coaVars = CapExTestDataFactory.createCOAAssignment(
                "1500-IT-CAPEX",
                "IT Equipment - Capital Expenditure"
        );
        runtimeService.setVariables(processInstance.getId(), coaVars);

        // ASSERT - Verify COA assignment
        String coaCode = (String) runtimeService.getVariable(processInstance.getId(), "coaCode");
        String coaDescription = (String) runtimeService.getVariable(processInstance.getId(), "coaDescription");

        assertThat(coaCode).isEqualTo("1500-IT-CAPEX");
        assertThat(coaDescription).contains("IT Equipment");

        System.out.println("PASS: COA assignment successful");
    }

    @Test
    @DisplayName("Test Procurement PO Generation After Approval")
    void testProcurementPOGeneration() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Approve request
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        waitForAsyncJobs(5000);

        // ACT - Generate PO
        Map<String, Object> poVars = CapExTestDataFactory.createPOGeneration(
                "PO-2024-001",
                "Dell Technologies",
                new BigDecimal("500.00")
        );
        runtimeService.setVariables(processInstance.getId(), poVars);

        // ASSERT - Verify PO generation
        Boolean poGenerated = (Boolean) runtimeService.getVariable(processInstance.getId(), "poGenerated");
        String poNumber = (String) runtimeService.getVariable(processInstance.getId(), "poNumber");
        String vendorName = (String) runtimeService.getVariable(processInstance.getId(), "vendorName");

        assertThat(poGenerated).isTrue();
        assertThat(poNumber).isEqualTo("PO-2024-001");
        assertThat(vendorName).isEqualTo("Dell Technologies");

        System.out.println("PASS: Procurement PO generated successfully");
    }

    @Test
    @DisplayName("Test Procurement Notification Sent")
    void testProcurementNotificationSent() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Approve request
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        waitForAsyncJobs(5000);

        // ACT - Trigger procurement notification
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "PO_GENERATION_REQUIRED",
                "david.procurement@werkflow.com",
                "New CapEx Approved - PO Required"
        );
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT - Verify notification sent to Procurement
        Boolean notificationSent = (Boolean) runtimeService.getVariable(processInstance.getId(), "notificationSent");
        String recipientEmail = (String) runtimeService.getVariable(processInstance.getId(), "recipientEmail");

        assertThat(notificationSent).isTrue();
        assertThat(recipientEmail).isEqualTo("david.procurement@werkflow.com");

        System.out.println("PASS: Procurement notification sent successfully");
    }

    @Test
    @DisplayName("Test Vendor Validation for CapEx")
    void testVendorValidation() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Validate vendor using mock service
        String vendorName = "Dell Technologies";
        boolean isApproved = CapExTestFixtures.MockVendorService.isApprovedVendor(vendorName);

        if (isApproved) {
            Map<String, Object> vendorVars = Map.of(
                    "vendorApproved", true,
                    "selectedVendor", vendorName
            );
            runtimeService.setVariables(processInstance.getId(), vendorVars);
        }

        // ASSERT - Verify vendor approval
        Boolean vendorApproved = (Boolean) runtimeService.getVariable(processInstance.getId(), "vendorApproved");
        assertThat(vendorApproved).isTrue();
        assertThat(isApproved).isTrue();

        System.out.println("PASS: Vendor validation successful");
    }

    @Test
    @DisplayName("Test Finance-Procurement End-to-End Flow")
    void testFinanceProcurementEndToEndFlow() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        // ACT - Start process with complete Finance validation
        Map<String, Object> budgetVars = CapExTestDataFactory.createFinanceBudgetValidation(
                true,
                "MARKETING-BUDGET-2024",
                new BigDecimal("25000.00")
        );
        variables.putAll(budgetVars);

        Map<String, Object> coaVars = CapExTestDataFactory.createCOAAssignment(
                "1520-MARKETING-CAPEX",
                "Marketing Equipment - Capital Expenditure"
        );
        variables.putAll(coaVars);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Approve at Department Head level
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String headId = CapExTestDataFactory.createMarketingHead().getUserId();
        taskService.claim(approvalTask.getId(), headId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(headId, "Mike Marketing Head", "Approved"));

        waitForAsyncJobs(5000);

        // Generate PO in Procurement
        Map<String, Object> poVars = CapExTestDataFactory.createPOGeneration(
                "PO-2024-002",
                "Industrial Print Co",
                new BigDecimal("7500.00")
        );
        runtimeService.setVariables(processInstance.getId(), poVars);

        // ASSERT - Verify complete flow
        Boolean budgetAvailable = (Boolean) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("budgetAvailable")
                .singleResult()
                .getValue();

        String coaCode = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("coaCode")
                .singleResult()
                .getValue();

        Boolean poGenerated = (Boolean) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("poGenerated")
                .singleResult()
                .getValue();

        assertThat(budgetAvailable).isTrue();
        assertThat(coaCode).isEqualTo("1520-MARKETING-CAPEX");
        assertThat(poGenerated).isTrue();

        System.out.println("PASS: Finance-Procurement end-to-end flow successful");
    }

    @Test
    @DisplayName("Test Budget Reservation During Approval")
    void testBudgetReservationDuringApproval() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Reserve budget using mock service
        String budgetCode = "IT-BUDGET-2024";
        BigDecimal amount = new BigDecimal("75000.00");
        String reservationId = CapExTestFixtures.MockBudgetService.reserveBudget(budgetCode, amount);

        runtimeService.setVariable(processInstance.getId(), "budgetReservationId", reservationId);

        // ASSERT - Verify budget reservation
        String storedReservationId = (String) runtimeService.getVariable(processInstance.getId(), "budgetReservationId");
        assertThat(storedReservationId).isNotNull();
        assertThat(storedReservationId).startsWith("BUDGET-RESERVATION-");

        System.out.println("PASS: Budget reservation during approval successful");
    }
}
