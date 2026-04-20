package com.werkflow.engine.fixtures;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.Map;

/**
 * Test fixtures for common workflow scenarios.
 * Provides helper methods to set up and verify workflow states.
 */
public class TestFixtures {

    /**
     * Starts a CapEx approval process with given variables.
     *
     * @param runtimeService Flowable RuntimeService
     * @param variables Process variables
     * @return Process instance ID
     */
    public static String startCapExProcess(RuntimeService runtimeService, Map<String, Object> variables) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );
        return processInstance.getId();
    }

    /**
     * Starts an HR leave request process with given variables.
     *
     * @param runtimeService Flowable RuntimeService
     * @param variables Process variables
     * @return Process instance ID
     */
    public static String startLeaveProcess(RuntimeService runtimeService, Map<String, Object> variables) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "leave-request-process",
                (String) variables.get("requestId"),
                variables
        );
        return processInstance.getId();
    }

    /**
     * Starts a procurement approval process with given variables.
     *
     * @param runtimeService Flowable RuntimeService
     * @param variables Process variables
     * @return Process instance ID
     */
    public static String startProcurementProcess(RuntimeService runtimeService, Map<String, Object> variables) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "procurement-approval-process",
                (String) variables.get("requestId"),
                variables
        );
        return processInstance.getId();
    }

    /**
     * Starts an asset transfer process with given variables.
     *
     * @param runtimeService Flowable RuntimeService
     * @param variables Process variables
     * @return Process instance ID
     */
    public static String startAssetTransferProcess(RuntimeService runtimeService, Map<String, Object> variables) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "asset-transfer-approval-process",
                (String) variables.get("requestId"),
                variables
        );
        return processInstance.getId();
    }

    /**
     * Completes a task with given variables.
     *
     * @param taskService Flowable TaskService
     * @param taskId Task ID
     * @param variables Task completion variables
     */
    public static void completeTask(TaskService taskService, String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }

    /**
     * Claims a task for a user.
     *
     * @param taskService Flowable TaskService
     * @param taskId Task ID
     * @param userId User ID
     */
    public static void claimTask(TaskService taskService, String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    /**
     * Delegates a task to another user.
     *
     * @param taskService Flowable TaskService
     * @param taskId Task ID
     * @param userId User ID to delegate to
     */
    public static void delegateTask(TaskService taskService, String taskId, String userId) {
        taskService.delegateTask(taskId, userId);
    }

    /**
     * Finds task by process instance ID and task definition key.
     *
     * @param taskService Flowable TaskService
     * @param processInstanceId Process instance ID
     * @param taskDefinitionKey Task definition key
     * @return Task or null if not found
     */
    public static Task findTaskByKey(TaskService taskService, String processInstanceId, String taskDefinitionKey) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
    }

    /**
     * Finds task by assignee.
     *
     * @param taskService Flowable TaskService
     * @param assignee Assignee user ID
     * @return Task or null if not found
     */
    public static Task findTaskByAssignee(TaskService taskService, String assignee) {
        return taskService.createTaskQuery()
                .taskAssignee(assignee)
                .singleResult();
    }

    /**
     * Finds task by candidate group.
     *
     * @param taskService Flowable TaskService
     * @param candidateGroup Candidate group ID
     * @return Task or null if not found
     */
    public static Task findTaskByCandidateGroup(TaskService taskService, String candidateGroup) {
        return taskService.createTaskQuery()
                .taskCandidateGroup(candidateGroup)
                .singleResult();
    }

    /**
     * Creates approval decision variables.
     *
     * @param approved Whether approved or not
     * @param comments Approver comments
     * @return Variables map
     */
    public static Map<String, Object> createApprovalVariables(boolean approved, String comments) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);
        variables.put("approvalDecision", approved ? "APPROVED" : "REJECTED");
        variables.put("approverComments", comments);
        variables.put("approvalTimestamp", System.currentTimeMillis());
        return variables;
    }

    /**
     * Creates delegation variables.
     *
     * @param originalAssignee Original assignee
     * @param delegateTo User to delegate to
     * @param reason Delegation reason
     * @return Variables map
     */
    public static Map<String, Object> createDelegationVariables(String originalAssignee,
                                                                  String delegateTo,
                                                                  String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalAssignee", originalAssignee);
        variables.put("delegateTo", delegateTo);
        variables.put("delegationReason", reason);
        variables.put("delegationTimestamp", System.currentTimeMillis());
        return variables;
    }

    /**
     * Verifies that a process variable has the expected value.
     *
     * @param runtimeService Flowable RuntimeService
     * @param processInstanceId Process instance ID
     * @param variableName Variable name
     * @param expectedValue Expected value
     * @return true if variable matches expected value
     */
    public static boolean verifyProcessVariable(RuntimeService runtimeService,
                                                  String processInstanceId,
                                                  String variableName,
                                                  Object expectedValue) {
        Object actualValue = runtimeService.getVariable(processInstanceId, variableName);
        return expectedValue == null ? actualValue == null : expectedValue.equals(actualValue);
    }

    /**
     * Waits for a specific amount of time (for async operations).
     *
     * @param milliseconds Milliseconds to wait
     */
    public static void waitFor(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
