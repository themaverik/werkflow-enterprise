package com.werkflow.engine.integration.capex;

import com.werkflow.engine.fixtures.IntegrationTestBase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejection and Resubmission Tests for CapEx Approval
 * Tests Scenario 3: $75K IT network infrastructure with initial rejection and resubmission
 */
@DisplayName("CapEx Rejection and Resubmission Tests")
class CapExRejectionResubmissionTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Rejection: Finance Manager rejects $75K request")
    void testFinanceManagerRejectsRequest() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        // ACT - Start process
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Get Finance Manager approval task
        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(financeTask).isNotNull();

        // Finance Manager claims and rejects task
        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);

        Map<String, Object> rejectionVars = CapExTestDataFactory.createRejectionDecision(
                financeManagerId,
                "Lisa Finance Manager",
                "Insufficient ROI justification. Current network is functional. " +
                "Please provide detailed cost-benefit analysis and phased implementation plan."
        );

        taskService.complete(financeTask.getId(), rejectionVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify process completed with rejection
        boolean isCompleted = isProcessCompleted(processInstanceId);
        assertThat(isCompleted).isTrue();

        // Verify rejection decision in history
        HistoricVariableInstance approvalDecision = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("approvalDecision")
                .singleResult();

        assertThat(approvalDecision).isNotNull();
        assertThat(approvalDecision.getValue()).isEqualTo("REJECTED");

        // Verify rejection reason is captured
        HistoricVariableInstance rejectionReason = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("rejectionReason")
                .singleResult();

        assertThat(rejectionReason).isNotNull();
        assertThat(rejectionReason.getValue()).asString().contains("Insufficient ROI justification");
        assertThat(rejectionReason.getValue()).asString().contains("cost-benefit analysis");

        System.out.println("PASS: Finance Manager rejection successful with detailed feedback");
    }

    @Test
    @DisplayName("Test Rejection Comments Captured")
    void testRejectionCommentsCaptured() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);

        // ACT - Reject with detailed comments
        String detailedRejectionReason = "REJECTION FEEDBACK:\n" +
                "1. ROI analysis missing - need 3-year payback calculation\n" +
                "2. No vendor quotes provided - require 3 competitive quotes\n" +
                "3. Implementation plan unclear - provide phased rollout schedule\n" +
                "4. Budget timing - Q1 budget already allocated, propose Q2 implementation\n" +
                "5. Security justification weak - provide risk assessment from InfoSec team";

        Map<String, Object> rejectionVars = CapExTestDataFactory.createRejectionDecision(
                financeManagerId,
                "Lisa Finance Manager",
                detailedRejectionReason
        );

        taskService.complete(financeTask.getId(), rejectionVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify all rejection details captured
        HistoricVariableInstance rejectionReason = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("rejectionReason")
                .singleResult();

        assertThat(rejectionReason).isNotNull();
        assertThat(rejectionReason.getValue()).asString().contains("ROI analysis missing");
        assertThat(rejectionReason.getValue()).asString().contains("vendor quotes");
        assertThat(rejectionReason.getValue()).asString().contains("phased rollout");
        assertThat(rejectionReason.getValue()).asString().contains("Budget timing");
        assertThat(rejectionReason.getValue()).asString().contains("Security justification");

        System.out.println("PASS: Detailed rejection comments fully captured");
    }

    @Test
    @DisplayName("Test Resubmission: IT resubmits with improvements")
    void testResubmissionWithImprovements() throws Exception {
        // ARRANGE - First submission (rejected)
        Map<String, Object> originalVariables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance originalProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) originalVariables.get("requestId"),
                originalVariables
        );

        String originalProcessId = originalProcess.getId();
        waitForAsyncJobs(5000);

        Task originalTask = taskService.createTaskQuery()
                .processInstanceId(originalProcessId)
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(originalTask.getId(), financeManagerId);

        Map<String, Object> rejectionVars = CapExTestDataFactory.createRejectionDecision(
                financeManagerId,
                "Lisa Finance Manager",
                "Need more detailed ROI analysis"
        );

        taskService.complete(originalTask.getId(), rejectionVars);
        waitForAsyncJobs(5000);

        // ACT - Resubmission with improvements
        Map<String, Object> resubmissionVariables = CapExTestDataFactory.createCapEx75KResubmission();

        ProcessInstance resubmissionProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) resubmissionVariables.get("requestId"),
                resubmissionVariables
        );

        String resubmissionProcessId = resubmissionProcess.getId();
        waitForAsyncJobs(5000);

        // ASSERT - Verify resubmission process started
        assertThat(resubmissionProcess).isNotNull();

        // Verify resubmission linking
        String originalRequestId = (String) runtimeService.getVariable(resubmissionProcessId, "originalRequestId");
        Integer resubmissionVersion = (Integer) runtimeService.getVariable(resubmissionProcessId, "resubmissionVersion");

        assertThat(originalRequestId).isEqualTo("CAPEX-2024-003");
        assertThat(resubmissionVersion).isEqualTo(1);

        // Verify improved justification is present
        String businessJustification = (String) runtimeService.getVariable(resubmissionProcessId, "businessJustification");
        assertThat(businessJustification).contains("ROI analysis");
        assertThat(businessJustification).contains("$50K saved annually");
        assertThat(businessJustification).contains("Phased implementation");

        System.out.println("PASS: Resubmission with improvements linked to original request");
    }

    @Test
    @DisplayName("Test Resubmission Approval After Rejection")
    void testResubmissionApprovalAfterRejection() throws Exception {
        // ARRANGE - Start resubmission process
        Map<String, Object> resubmissionVariables = CapExTestDataFactory.createCapEx75KResubmission();

        ProcessInstance resubmissionProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) resubmissionVariables.get("requestId"),
                resubmissionVariables
        );

        String resubmissionProcessId = resubmissionProcess.getId();
        waitForAsyncJobs(5000);

        Task resubmissionTask = taskService.createTaskQuery()
                .processInstanceId(resubmissionProcessId)
                .singleResult();

        assertThat(resubmissionTask).isNotNull();

        // ACT - Finance Manager approves resubmission
        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(resubmissionTask.getId(), financeManagerId);

        Map<String, Object> approvalVars = CapExTestDataFactory.createApprovalDecision(
                financeManagerId,
                "Lisa Finance Manager",
                "Approved after review. Improved ROI analysis addresses previous concerns. " +
                "Phased implementation plan is acceptable."
        );

        taskService.complete(resubmissionTask.getId(), approvalVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify process completed with approval
        boolean isCompleted = isProcessCompleted(resubmissionProcessId);
        assertThat(isCompleted).isTrue();

        // Verify approval decision
        HistoricVariableInstance approvalDecision = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(resubmissionProcessId)
                .variableName("approvalDecision")
                .singleResult();

        assertThat(approvalDecision).isNotNull();
        assertThat(approvalDecision.getValue()).isEqualTo("APPROVED");

        // Verify approver comments reference previous rejection
        HistoricVariableInstance approverComments = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(resubmissionProcessId)
                .variableName("approverComments")
                .singleResult();

        assertThat(approverComments.getValue()).asString().contains("Approved after review");
        assertThat(approverComments.getValue()).asString().contains("previous concerns");

        System.out.println("PASS: Resubmission approved successfully after addressing feedback");
    }

    @Test
    @DisplayName("Test Version Tracking for Multiple Resubmissions")
    void testVersionTrackingMultipleResubmissions() throws Exception {
        // ARRANGE - Original submission
        Map<String, Object> originalVars = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        originalVars.put("requestId", "CAPEX-2024-VERSION-TEST");

        ProcessInstance originalProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-VERSION-TEST",
                originalVars
        );

        waitForAsyncJobs(5000);

        // Reject original
        Task originalTask = taskService.createTaskQuery()
                .processInstanceId(originalProcess.getId())
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(originalTask.getId(), financeManagerId);
        taskService.complete(originalTask.getId(),
                CapExTestDataFactory.createRejectionDecision(financeManagerId, "Lisa Finance Manager", "Rejection 1"));

        waitForAsyncJobs(5000);

        // ACT - First resubmission (Version 1)
        Map<String, Object> resubmissionV1 = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        resubmissionV1.put("requestId", "CAPEX-2024-VERSION-TEST-R1");
        resubmissionV1.put("originalRequestId", "CAPEX-2024-VERSION-TEST");
        resubmissionV1.put("resubmissionVersion", 1);

        ProcessInstance resubV1Process = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-VERSION-TEST-R1",
                resubmissionV1
        );

        waitForAsyncJobs(5000);

        // Reject resubmission V1
        Task resubV1Task = taskService.createTaskQuery()
                .processInstanceId(resubV1Process.getId())
                .singleResult();

        taskService.claim(resubV1Task.getId(), financeManagerId);
        taskService.complete(resubV1Task.getId(),
                CapExTestDataFactory.createRejectionDecision(financeManagerId, "Lisa Finance Manager", "Rejection 2"));

        waitForAsyncJobs(5000);

        // Second resubmission (Version 2)
        Map<String, Object> resubmissionV2 = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        resubmissionV2.put("requestId", "CAPEX-2024-VERSION-TEST-R2");
        resubmissionV2.put("originalRequestId", "CAPEX-2024-VERSION-TEST");
        resubmissionV2.put("resubmissionVersion", 2);

        ProcessInstance resubV2Process = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-VERSION-TEST-R2",
                resubmissionV2
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify version tracking
        Integer version = (Integer) runtimeService.getVariable(resubV2Process.getId(), "resubmissionVersion");
        String originalId = (String) runtimeService.getVariable(resubV2Process.getId(), "originalRequestId");

        assertThat(version).isEqualTo(2);
        assertThat(originalId).isEqualTo("CAPEX-2024-VERSION-TEST");

        System.out.println("PASS: Version tracking for multiple resubmissions working correctly");
    }

    @Test
    @DisplayName("Test Requester Notification on Rejection")
    void testRequesterNotificationOnRejection() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);

        // ACT - Reject with notification tracking
        Map<String, Object> rejectionVars = CapExTestDataFactory.createRejectionDecision(
                financeManagerId,
                "Lisa Finance Manager",
                "Please resubmit with improvements"
        );

        taskService.complete(financeTask.getId(), rejectionVars);

        // Add notification tracking
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "REQUEST_REJECTED",
                "john.it@werkflow.com",
                "Your CapEx Request has been Rejected"
        );
        runtimeService.setVariables(processInstanceId, notificationVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify notification sent to requester
        Boolean notificationSent = (Boolean) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("notificationSent")
                .singleResult()
                .getValue();

        String recipientEmail = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("recipientEmail")
                .singleResult()
                .getValue();

        assertThat(notificationSent).isTrue();
        assertThat(recipientEmail).isEqualTo("john.it@werkflow.com");

        System.out.println("PASS: Rejection notification sent to requester");
    }

    @Test
    @DisplayName("Test Conditional Re-routing After Rejection")
    void testConditionalReRoutingAfterRejection() throws Exception {
        // ARRANGE - Resubmission with different amount (lower DOA level)
        Map<String, Object> originalVars = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        // Simulate rejection at Level 3
        ProcessInstance originalProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) originalVars.get("requestId"),
                originalVars
        );

        waitForAsyncJobs(5000);

        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(originalProcess.getId())
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);
        taskService.complete(financeTask.getId(),
                CapExTestDataFactory.createRejectionDecision(financeManagerId, "Lisa Finance Manager",
                        "Amount too high. Reduce scope to $7,500 for initial phase."));

        waitForAsyncJobs(5000);

        // ACT - Resubmit with reduced amount (should route to Level 2 instead of Level 3)
        Map<String, Object> reducedResubmission = CapExTestDataFactory.createCapEx7500PrintingEquipment();
        reducedResubmission.put("requestId", "CAPEX-2024-003-REDUCED");
        reducedResubmission.put("originalRequestId", "CAPEX-2024-003");
        reducedResubmission.put("resubmissionVersion", 1);
        reducedResubmission.put("title", "Network Infrastructure - Phase 1");
        reducedResubmission.put("description", "Reduced scope: Core switches only");
        reducedResubmission.put("doaLevel", 2); // Now routes to Department Head

        ProcessInstance reducedProcess = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-003-REDUCED",
                reducedResubmission
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify routing changed to Level 2
        Integer newDoaLevel = (Integer) runtimeService.getVariable(reducedProcess.getId(), "doaLevel");
        assertThat(newDoaLevel).isEqualTo(2);

        Task newTask = taskService.createTaskQuery()
                .processInstanceId(reducedProcess.getId())
                .singleResult();

        // Verify task assigned to Department Head role (not Finance Manager)
        assertThat(taskService.getIdentityLinksForTask(newTask.getId()))
                .anyMatch(link -> link.getGroupId() != null && link.getGroupId().contains("HEAD"));

        System.out.println("PASS: Conditional re-routing based on reduced amount successful");
    }
}
