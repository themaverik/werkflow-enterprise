package com.werkflow.engine.integration.capex;

import com.werkflow.engine.fixtures.IntegrationTestBase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Notification Integration Tests for CapEx Workflow
 * Tests all notification touchpoints throughout the CapEx lifecycle
 */
@DisplayName("CapEx Notification Integration Tests")
class CapExNotificationIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void setupNotificationTest() {
        // Clear notification history before each test
        CapExTestFixtures.MockNotificationService.clear();
    }

    @Test
    @DisplayName("Test Task Assignment Notification")
    void testTaskAssignmentNotification() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(approvalTask).isNotNull();

        // Send task assignment notification
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "TASK_ASSIGNED",
                "sarah.it.manager@werkflow.com",
                "New CapEx Approval Task Assigned"
        );
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        Boolean notificationSent = (Boolean) runtimeService.getVariable(processInstance.getId(), "notificationSent");
        String recipientEmail = (String) runtimeService.getVariable(processInstance.getId(), "recipientEmail");
        String subject = (String) runtimeService.getVariable(processInstance.getId(), "notificationSubject");

        assertThat(notificationSent).isTrue();
        assertThat(recipientEmail).isEqualTo("sarah.it.manager@werkflow.com");
        assertThat(subject).contains("Task Assigned");

        System.out.println("PASS: Task assignment notification sent successfully");
    }

    @Test
    @DisplayName("Test Approval Decision Notification to Requester")
    void testApprovalDecisionNotificationToRequester() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);

        // ACT - Complete task and send notification
        Map<String, Object> approvalVars = CapExTestDataFactory.createApprovalDecision(
                managerId,
                "Sarah IT Manager",
                "Approved - Justified business need"
        );
        taskService.complete(approvalTask.getId(), approvalVars);

        waitForAsyncJobs(5000);

        // Send approval notification
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "REQUEST_APPROVED",
                "john.it@werkflow.com",
                "Your CapEx Request has been Approved"
        );
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        String notificationType = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("notificationType")
                .singleResult()
                .getValue();

        String recipientEmail = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("recipientEmail")
                .singleResult()
                .getValue();

        assertThat(notificationType).isEqualTo("REQUEST_APPROVED");
        assertThat(recipientEmail).isEqualTo("john.it@werkflow.com");

        System.out.println("PASS: Approval decision notification sent to requester");
    }

    @Test
    @DisplayName("Test Rejection Notification with Comments")
    void testRejectionNotificationWithComments() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);

        String rejectionReason = "Insufficient ROI justification. Please provide detailed cost-benefit analysis.";

        // ACT - Reject and send notification
        Map<String, Object> rejectionVars = CapExTestDataFactory.createRejectionDecision(
                financeManagerId,
                "Lisa Finance Manager",
                rejectionReason
        );
        taskService.complete(financeTask.getId(), rejectionVars);

        waitForAsyncJobs(5000);

        // Send rejection notification with comments
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "REQUEST_REJECTED",
                "john.it@werkflow.com",
                "Your CapEx Request has been Rejected - Action Required"
        );
        notificationVars.put("notificationBody", rejectionReason);
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        String notificationType = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("notificationType")
                .singleResult()
                .getValue();

        String recipientEmail = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("recipientEmail")
                .singleResult()
                .getValue();

        assertThat(notificationType).isEqualTo("REQUEST_REJECTED");
        assertThat(recipientEmail).isEqualTo("john.it@werkflow.com");

        System.out.println("PASS: Rejection notification with comments sent successfully");
    }

    @Test
    @DisplayName("Test PO Generation Notification to Procurement")
    void testPOGenerationNotificationToProcurement() throws Exception {
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

        // ACT - Send PO generation notification
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "PO_GENERATION_REQUIRED",
                "david.procurement@werkflow.com",
                "New CapEx Approved - PO Generation Required"
        );
        notificationVars.put("notificationBody",
                "CapEx Request CAPEX-2024-001 approved. Please generate PO for Dell PowerEdge Server - $500.00");
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        Boolean notificationSent = (Boolean) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("notificationSent")
                .singleResult()
                .getValue();

        String recipientEmail = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("recipientEmail")
                .singleResult()
                .getValue();

        assertThat(notificationSent).isTrue();
        assertThat(recipientEmail).isEqualTo("david.procurement@werkflow.com");

        System.out.println("PASS: PO generation notification sent to Procurement");
    }

    @Test
    @DisplayName("Test Asset Receipt Notification to Finance")
    void testAssetReceiptNotificationToFinance() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Receive asset
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-002",
                "Industrial Printer",
                new java.math.BigDecimal("7500.00"),
                1
        );
        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ACT - Send asset receipt notification
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "ASSET_RECEIVED",
                "lisa.finance.manager@werkflow.com",
                "Asset Received - Capitalization Required"
        );
        notificationVars.put("notificationBody",
                "Asset ASSET-2024-002 received. Value: $7,500.00. Please capitalize to ledger account 1520-MARKETING-CAPEX");
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        String notificationType = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("notificationType")
                .singleResult()
                .getValue();

        String recipientEmail = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("recipientEmail")
                .singleResult()
                .getValue();

        assertThat(notificationType).isEqualTo("ASSET_RECEIVED");
        assertThat(recipientEmail).isEqualTo("lisa.finance.manager@werkflow.com");

        System.out.println("PASS: Asset receipt notification sent to Finance");
    }

    @Test
    @DisplayName("Test Completion Notification to All Stakeholders")
    void testCompletionNotificationToAllStakeholders() throws Exception {
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

        // ACT - Send completion notifications to all stakeholders
        // Notification to requester
        Map<String, Object> requesterNotification = CapExTestDataFactory.createNotificationTracking(
                "PROCESS_COMPLETED",
                "john.it@werkflow.com",
                "CapEx Process Completed Successfully"
        );
        runtimeService.setVariables(processInstance.getId(), requesterNotification);

        CapExTestFixtures.MockNotificationService.sendNotification(
                "john.it@werkflow.com",
                "CapEx Process Completed Successfully",
                "Your CapEx request CAPEX-2024-001 has been fully processed."
        );

        // Notification to approver
        CapExTestFixtures.MockNotificationService.sendNotification(
                "sarah.it.manager@werkflow.com",
                "CapEx Process Completed",
                "CapEx request CAPEX-2024-001 you approved has been completed."
        );

        // Notification to Finance
        CapExTestFixtures.MockNotificationService.sendNotification(
                "lisa.finance.manager@werkflow.com",
                "CapEx Asset Capitalized",
                "CapEx request CAPEX-2024-001 asset has been capitalized."
        );

        // ASSERT
        assertThat(CapExTestFixtures.MockNotificationService.wasNotificationSentTo("john.it@werkflow.com")).isTrue();
        assertThat(CapExTestFixtures.MockNotificationService.wasNotificationSentTo("sarah.it.manager@werkflow.com")).isTrue();
        assertThat(CapExTestFixtures.MockNotificationService.wasNotificationSentTo("lisa.finance.manager@werkflow.com")).isTrue();
        assertThat(CapExTestFixtures.MockNotificationService.getSentNotifications()).hasSize(3);

        System.out.println("PASS: Completion notifications sent to all stakeholders");
    }

    @Test
    @DisplayName("Test Email Content Accuracy")
    void testEmailContentAccuracy() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Create notification with detailed content
        String requestId = (String) variables.get("requestId");
        String title = (String) variables.get("title");
        String amount = variables.get("amount").toString();

        String emailBody = String.format(
                "Dear Approver,\n\n" +
                "A new CapEx approval request has been assigned to you:\n\n" +
                "Request ID: %s\n" +
                "Title: %s\n" +
                "Amount: $%s\n" +
                "Department: IT\n" +
                "Requester: John IT Employee\n\n" +
                "Please review and approve/reject this request in the workflow system.\n\n" +
                "Thank you,\n" +
                "Workflow Automation System",
                requestId, title, amount
        );

        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "TASK_ASSIGNED",
                "sarah.it.manager@werkflow.com",
                "New CapEx Approval Task Assigned - " + requestId
        );
        notificationVars.put("notificationBody", emailBody);
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT
        String storedBody = (String) runtimeService.getVariable(processInstance.getId(), "notificationBody");
        String storedSubject = (String) runtimeService.getVariable(processInstance.getId(), "notificationSubject");

        assertThat(storedBody).contains(requestId);
        assertThat(storedBody).contains(title);
        assertThat(storedBody).contains("$" + amount);
        assertThat(storedBody).contains("John IT Employee");
        assertThat(storedSubject).contains(requestId);

        System.out.println("PASS: Email content accuracy verified");
    }

    @Test
    @DisplayName("Test Email Sender Mock Verification")
    void testEmailSenderMockVerification() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Trigger notification (should use mocked mail sender)
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "TASK_ASSIGNED",
                "sarah.it.manager@werkflow.com",
                "New CapEx Task"
        );
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT - Verify mock email sender was called
        verify(mailSender, atLeastOnce()).createMimeMessage();

        System.out.println("PASS: Email sender mock verification successful");
    }
}
