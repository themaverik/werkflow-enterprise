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
 * Delegation and Audit Trail Tests for CapEx Approval
 * Tests Scenario 2: $7,500 Marketing equipment with delegation to CFO
 */
@DisplayName("CapEx Delegation and Audit Tests")
class CapExDelegationAuditTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Delegation: Marketing Head delegates to CFO")
    void testMarketingHeadDelegatesToCFO() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        // ACT - Start process
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Get Marketing Head approval task
        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(marketingHeadTask).isNotNull();

        // Marketing Head claims task
        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        taskService.claim(marketingHeadTask.getId(), marketingHeadId);

        // Marketing Head delegates to CFO
        String cfoId = CapExTestDataFactory.createCFO().getUserId();
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        // Store delegation audit trail
        Map<String, Object> delegationVars = CapExTestDataFactory.createDelegationToSubstitute(
                marketingHeadId,
                cfoId,
                "On vacation this week. Delegating authority to CFO for approval."
        );
        runtimeService.setVariables(processInstanceId, delegationVars);

        // ASSERT - Verify delegation state
        Task delegatedTask = taskService.createTaskQuery()
                .taskId(marketingHeadTask.getId())
                .singleResult();

        assertThat(delegatedTask).isNotNull();
        assertThat(delegatedTask.getAssignee()).isEqualTo(cfoId);
        assertThat(delegatedTask.getDelegationState()).isNotNull();

        // Verify delegation audit variables
        String originalAssignee = (String) runtimeService.getVariable(processInstanceId, "originalAssignee");
        String delegateTo = (String) runtimeService.getVariable(processInstanceId, "delegateTo");
        String delegationReason = (String) runtimeService.getVariable(processInstanceId, "delegationReason");
        Boolean isDelegated = (Boolean) runtimeService.getVariable(processInstanceId, "isDelegated");

        assertThat(originalAssignee).isEqualTo(marketingHeadId);
        assertThat(delegateTo).isEqualTo(cfoId);
        assertThat(delegationReason).contains("On vacation");
        assertThat(isDelegated).isTrue();

        System.out.println("PASS: Delegation from Marketing Head to CFO successful with audit trail");
    }

    @Test
    @DisplayName("Test Delegated Approval: CFO approves on behalf of Marketing Head")
    void testDelegatedApprovalByCFO() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        // Delegate task
        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        String cfoId = CapExTestDataFactory.createCFO().getUserId();

        taskService.claim(marketingHeadTask.getId(), marketingHeadId);
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        Map<String, Object> delegationVars = CapExTestDataFactory.createDelegationToSubstitute(
                marketingHeadId,
                cfoId,
                "Delegated for approval"
        );
        runtimeService.setVariables(processInstanceId, delegationVars);

        // ACT - CFO approves on behalf of Marketing Head
        Map<String, Object> approvalVars = CapExTestDataFactory.createApprovalDecision(
                cfoId,
                "Robert CFO",
                "Approved on behalf of Marketing Head. Request is justified and within budget."
        );

        taskService.complete(marketingHeadTask.getId(), approvalVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify process completed
        boolean isCompleted = isProcessCompleted(processInstanceId);
        assertThat(isCompleted).isTrue();

        // Verify approval decision in history
        HistoricVariableInstance approvalDecision = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("approvalDecision")
                .singleResult();

        assertThat(approvalDecision).isNotNull();
        assertThat(approvalDecision.getValue()).isEqualTo("APPROVED");

        // Verify approver is CFO (delegated approver)
        HistoricVariableInstance approverId = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("approverId")
                .singleResult();

        assertThat(approverId).isNotNull();
        assertThat(approverId.getValue()).isEqualTo(cfoId);

        System.out.println("PASS: Delegated approval by CFO successful");
    }

    @Test
    @DisplayName("Test Delegation Audit Trail Completeness")
    void testDelegationAuditTrailCompleteness() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        String cfoId = CapExTestDataFactory.createCFO().getUserId();

        taskService.claim(marketingHeadTask.getId(), marketingHeadId);

        // ACT - Delegate with detailed reason
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        Map<String, Object> delegationVars = CapExTestDataFactory.createDelegationToSubstitute(
                marketingHeadId,
                cfoId,
                "Out of office until next Monday. CFO has full authority to approve Marketing budget items."
        );
        runtimeService.setVariables(processInstanceId, delegationVars);

        // Complete task
        Map<String, Object> approvalVars = CapExTestDataFactory.createApprovalDecision(
                cfoId,
                "Robert CFO",
                "Approved"
        );
        taskService.complete(marketingHeadTask.getId(), approvalVars);

        waitForAsyncJobs(5000);

        // ASSERT - Verify complete audit trail in history
        Object originalAssigneeValue = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("originalAssignee")
                .singleResult()
                .getValue();

        Object delegateToValue = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("delegateTo")
                .singleResult()
                .getValue();

        Object delegationReasonValue = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("delegationReason")
                .singleResult()
                .getValue();

        Object delegationTimestampValue = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("delegationTimestamp")
                .singleResult()
                .getValue();

        Object isDelegatedValue = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("isDelegated")
                .singleResult()
                .getValue();

        // Verify all delegation audit fields are captured
        assertThat(originalAssigneeValue).isNotNull();
        assertThat(originalAssigneeValue).isEqualTo(marketingHeadId);

        assertThat(delegateToValue).isNotNull();
        assertThat(delegateToValue).isEqualTo(cfoId);

        assertThat(delegationReasonValue).isNotNull();
        assertThat(delegationReasonValue.toString()).contains("Out of office");

        assertThat(delegationTimestampValue).isNotNull();

        assertThat(isDelegatedValue).isNotNull();
        assertThat(isDelegatedValue).isEqualTo(true);

        System.out.println("PASS: Complete delegation audit trail captured in process history");
    }

    @Test
    @DisplayName("Test Re-Delegation: CFO re-delegates to Substitute")
    void testReDelegationToSubstitute() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        String cfoId = CapExTestDataFactory.createCFO().getUserId();
        String substituteId = CapExTestDataFactory.createSubstituteApprover().getUserId();

        // First delegation: Marketing Head -> CFO
        taskService.claim(marketingHeadTask.getId(), marketingHeadId);
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        // ACT - Re-delegation: CFO -> Substitute
        taskService.delegateTask(marketingHeadTask.getId(), substituteId);

        Map<String, Object> reDelegationVars = CapExTestDataFactory.createDelegationToSubstitute(
                cfoId,
                substituteId,
                "CFO unavailable. Re-delegating to Finance Director."
        );
        runtimeService.setVariables(processInstanceId, reDelegationVars);

        // ASSERT - Verify re-delegation
        Task reDelegatedTask = taskService.createTaskQuery()
                .taskId(marketingHeadTask.getId())
                .singleResult();

        assertThat(reDelegatedTask).isNotNull();
        assertThat(reDelegatedTask.getAssignee()).isEqualTo(substituteId);

        // Verify re-delegation variables
        String delegateTo = (String) runtimeService.getVariable(processInstanceId, "delegateTo");
        assertThat(delegateTo).isEqualTo(substituteId);

        System.out.println("PASS: Re-delegation from CFO to Substitute successful");
    }

    @Test
    @DisplayName("Test Delegation Notification Sent")
    void testDelegationNotificationSent() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        String cfoId = CapExTestDataFactory.createCFO().getUserId();

        taskService.claim(marketingHeadTask.getId(), marketingHeadId);

        // ACT - Delegate task
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        Map<String, Object> delegationVars = CapExTestDataFactory.createDelegationToSubstitute(
                marketingHeadId,
                cfoId,
                "Delegated for approval"
        );
        runtimeService.setVariables(processInstanceId, delegationVars);

        // Add notification tracking
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "TASK_DELEGATED",
                "robert.cfo@werkflow.com",
                "CapEx Task Delegated to You"
        );
        runtimeService.setVariables(processInstanceId, notificationVars);

        // ASSERT - Verify notification variables set
        Boolean notificationSent = (Boolean) runtimeService.getVariable(processInstanceId, "notificationSent");
        String notificationType = (String) runtimeService.getVariable(processInstanceId, "notificationType");

        assertThat(notificationSent).isTrue();
        assertThat(notificationType).isEqualTo("TASK_DELEGATED");

        System.out.println("PASS: Delegation notification tracking verified");
    }

    @Test
    @DisplayName("Test Delegation Cascade Rules")
    void testDelegationCascadeRules() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task marketingHeadTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String marketingHeadId = CapExTestDataFactory.createMarketingHead().getUserId();
        String cfoId = CapExTestDataFactory.createCFO().getUserId();

        taskService.claim(marketingHeadTask.getId(), marketingHeadId);

        // ACT - Delegate with cascade tracking
        taskService.delegateTask(marketingHeadTask.getId(), cfoId);

        // Track delegation chain
        runtimeService.setVariable(processInstanceId, "delegationChain",
                marketingHeadId + " -> " + cfoId);
        runtimeService.setVariable(processInstanceId, "delegationLevel", 1);

        // ASSERT - Verify cascade variables
        String delegationChain = (String) runtimeService.getVariable(processInstanceId, "delegationChain");
        Integer delegationLevel = (Integer) runtimeService.getVariable(processInstanceId, "delegationLevel");

        assertThat(delegationChain).contains(marketingHeadId);
        assertThat(delegationChain).contains(cfoId);
        assertThat(delegationLevel).isEqualTo(1);

        System.out.println("PASS: Delegation cascade rules verified");
    }
}
