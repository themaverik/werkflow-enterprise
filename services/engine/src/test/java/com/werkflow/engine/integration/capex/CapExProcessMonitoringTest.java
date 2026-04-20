package com.werkflow.engine.integration.capex;

import com.werkflow.engine.fixtures.IntegrationTestBase;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Process Monitoring Tests for CapEx Workflow
 * Tests process history, audit trail, and monitoring APIs
 */
@DisplayName("CapEx Process Monitoring Tests")
class CapExProcessMonitoringTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Process Details Endpoint - In Progress")
    void testProcessDetailsInProgress() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify process instance details
        ProcessInstance foundProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(foundProcess).isNotNull();
        assertThat(foundProcess.getProcessDefinitionKey()).isEqualTo("capex-approval-process");
        assertThat(foundProcess.getBusinessKey()).isEqualTo(variables.get("requestId"));
        assertThat(foundProcess.isEnded()).isFalse();

        System.out.println("PASS: Process details retrieved for in-progress process");
    }

    @Test
    @DisplayName("Test Task History Completeness")
    void testTaskHistoryCompleteness() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Complete approval task
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);

        long startTime = System.currentTimeMillis();
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));
        long endTime = System.currentTimeMillis();

        waitForAsyncJobs(5000);

        // ACT - Query task history
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        // ASSERT - Verify task history completeness
        assertThat(historicTasks).isNotEmpty();

        HistoricTaskInstance historicTask = historicTasks.get(0);
        assertThat(historicTask.getName()).isEqualTo("Manager Review");
        assertThat(historicTask.getAssignee()).isEqualTo(managerId);
        assertThat(historicTask.getStartTime()).isNotNull();
        assertThat(historicTask.getEndTime()).isNotNull();
        assertThat(historicTask.getEndTime()).isAfterOrEqualTo(historicTask.getStartTime());
        assertThat(historicTask.getDurationInMillis()).isGreaterThanOrEqualTo(0);

        System.out.println("PASS: Task history completeness verified");
    }

    @Test
    @DisplayName("Test Event Timeline")
    void testEventTimeline() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Complete approval task
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        waitForAsyncJobs(5000);

        // ACT - Query activity history (event timeline)
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        // ASSERT - Verify event timeline
        assertThat(activities).isNotEmpty();

        // Verify start event exists
        assertThat(activities).anyMatch(activity ->
                activity.getActivityType().equals("startEvent"));

        // Verify service tasks exist (budget check, etc.)
        assertThat(activities).anyMatch(activity ->
                activity.getActivityType().equals("serviceTask"));

        // Verify user task exists (manager approval)
        assertThat(activities).anyMatch(activity ->
                activity.getActivityType().equals("userTask"));

        // Verify chronological order
        for (int i = 1; i < activities.size(); i++) {
            assertThat(activities.get(i).getStartTime())
                    .isAfterOrEqualTo(activities.get(i - 1).getStartTime());
        }

        System.out.println("PASS: Event timeline verified in chronological order");
    }

    @Test
    @DisplayName("Test Business Key Query (CAPEX-YYYY-NNNNN)")
    void testBusinessKeyQuery() throws Exception {
        // ARRANGE - Create multiple CapEx requests
        Map<String, Object> request1 = CapExTestDataFactory.createCapEx500ServerUpgrade();
        request1.put("requestId", "CAPEX-2024-001");

        Map<String, Object> request2 = CapExTestDataFactory.createCapEx7500PrintingEquipment();
        request2.put("requestId", "CAPEX-2024-002");

        Map<String, Object> request3 = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        request3.put("requestId", "CAPEX-2024-003");

        // ACT - Start processes with business keys
        ProcessInstance process1 = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-001",
                request1
        );

        ProcessInstance process2 = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-002",
                request2
        );

        ProcessInstance process3 = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-003",
                request3
        );

        waitForAsyncJobs(5000);

        // Query by specific business key
        ProcessInstance foundProcess1 = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("CAPEX-2024-001")
                .singleResult();

        ProcessInstance foundProcess2 = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("CAPEX-2024-002")
                .singleResult();

        ProcessInstance foundProcess3 = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("CAPEX-2024-003")
                .singleResult();

        // ASSERT - Verify business key queries
        assertThat(foundProcess1).isNotNull();
        assertThat(foundProcess1.getBusinessKey()).isEqualTo("CAPEX-2024-001");

        assertThat(foundProcess2).isNotNull();
        assertThat(foundProcess2.getBusinessKey()).isEqualTo("CAPEX-2024-002");

        assertThat(foundProcess3).isNotNull();
        assertThat(foundProcess3.getBusinessKey()).isEqualTo("CAPEX-2024-003");

        // Query all CapEx processes
        List<ProcessInstance> allCapExProcesses = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey("capex-approval-process")
                .list();

        assertThat(allCapExProcesses).hasSizeGreaterThanOrEqualTo(3);

        System.out.println("PASS: Business key queries successful");
    }

    @Test
    @DisplayName("Test Process Status Transitions")
    void testProcessStatusTransitions() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        // ACT - Start process (status: RUNNING)
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT - Verify status is RUNNING
        ProcessInstance runningProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(runningProcess).isNotNull();
        assertThat(runningProcess.isEnded()).isFalse();

        // Complete approval task
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        waitForAsyncJobs(5000);

        // ASSERT - Verify status is COMPLETED
        ProcessInstance completedProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(completedProcess).isNull(); // Process is completed, not in runtime table

        // Verify in history
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(historicProcess).isNotNull();
        assertThat(historicProcess.getEndTime()).isNotNull();
        assertThat(historicProcess.getDurationInMillis()).isGreaterThan(0);

        System.out.println("PASS: Process status transitions verified");
    }

    @Test
    @DisplayName("Test Audit Trail in Monitoring API")
    void testAuditTrailInMonitoringAPI() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Reject request
        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String financeManagerId = CapExTestDataFactory.createFinanceManager().getUserId();
        taskService.claim(financeTask.getId(), financeManagerId);
        taskService.complete(financeTask.getId(),
                CapExTestDataFactory.createRejectionDecision(
                        financeManagerId,
                        "Lisa Finance Manager",
                        "Insufficient ROI justification"
                ));

        waitForAsyncJobs(5000);

        // ACT - Query complete audit trail
        List<HistoricVariableInstance> historicVariables = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByVariableName()
                .asc()
                .list();

        // ASSERT - Verify audit trail completeness
        assertThat(historicVariables).isNotEmpty();

        // Verify key audit fields
        assertThat(historicVariables).anyMatch(var ->
                var.getVariableName().equals("requestId"));
        assertThat(historicVariables).anyMatch(var ->
                var.getVariableName().equals("approvalDecision"));
        assertThat(historicVariables).anyMatch(var ->
                var.getVariableName().equals("rejectionReason"));
        assertThat(historicVariables).anyMatch(var ->
                var.getVariableName().equals("approverId"));

        // Verify rejection reason captured
        HistoricVariableInstance rejectionReason = historicVariables.stream()
                .filter(var -> var.getVariableName().equals("rejectionReason"))
                .findFirst()
                .orElse(null);

        assertThat(rejectionReason).isNotNull();
        assertThat(rejectionReason.getValue()).asString().contains("Insufficient ROI");

        System.out.println("PASS: Complete audit trail verified in monitoring API");
    }

    @Test
    @DisplayName("Test Process Performance Metrics")
    void testProcessPerformanceMetrics() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        long processStartTime = System.currentTimeMillis();

        // ACT - Execute complete process
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        long taskStartTime = System.currentTimeMillis();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);

        Thread.sleep(100); // Simulate processing time

        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        long taskEndTime = System.currentTimeMillis();

        waitForAsyncJobs(5000);

        long processEndTime = System.currentTimeMillis();

        // ASSERT - Verify performance metrics
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(historicProcess.getDurationInMillis()).isGreaterThan(0);
        assertThat(historicProcess.getDurationInMillis()).isLessThan(30000); // Should complete within 30 seconds

        HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey("managerApproval")
                .singleResult();

        assertThat(historicTask.getDurationInMillis()).isGreaterThan(0);
        assertThat(historicTask.getDurationInMillis()).isLessThan(10000); // Task should complete within 10 seconds

        // Calculate and verify p95 response time (simplified - in real scenario would use multiple samples)
        long totalDuration = historicProcess.getDurationInMillis();
        assertThat(totalDuration).isLessThan(500); // Target: <500ms p95

        System.out.println("PASS: Process performance metrics verified (Duration: " + totalDuration + "ms)");
    }

    @Test
    @DisplayName("Test Variable History Tracking")
    void testVariableHistoryTracking() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // Update variables during process execution
        runtimeService.setVariable(processInstanceId, "status", "PENDING_APPROVAL");

        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        String headId = CapExTestDataFactory.createMarketingHead().getUserId();
        taskService.claim(approvalTask.getId(), headId);

        runtimeService.setVariable(processInstanceId, "status", "IN_REVIEW");

        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(headId, "Mike Marketing Head", "Approved"));

        waitForAsyncJobs(5000);

        runtimeService.setVariable(processInstanceId, "status", "APPROVED");

        // ACT - Query variable history
        List<HistoricVariableInstance> statusHistory = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("status")
                .list();

        // ASSERT - Verify variable changes tracked
        assertThat(statusHistory).isNotEmpty();

        // Latest value should be APPROVED
        HistoricVariableInstance latestStatus = statusHistory.get(statusHistory.size() - 1);
        assertThat(latestStatus.getValue()).isEqualTo("APPROVED");

        System.out.println("PASS: Variable history tracking verified");
    }
}
