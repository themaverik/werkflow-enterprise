package com.werkflow.delegates.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Approval Delegate for standard approval logic
 *
 * Configurable via BPMN process variables:
 * - approverRole: Role/group of approvers (required)
 * - approverUserId: Specific user ID to assign (optional, overrides role)
 * - escalationEnabled: Enable escalation (default: true)
 * - escalationTimeMinutes: Minutes before escalation (default: 1440 = 24 hours)
 * - escalationRole: Role to escalate to (optional, defaults to manager)
 * - autoApproveThreshold: Auto-approve if amount below threshold (optional)
 * - requireComment: Require comment on rejection (default: true)
 * - notifyApprover: Send notification to approver (default: true)
 *
 * Sets the following variables:
 * - approvalPending: boolean
 * - approvalAssignedTo: user ID or role
 * - approvalDueDate: escalation due date
 * - approvalStartTime: when approval started
 *
 * Example BPMN configuration:
 * <serviceTask id="setupApproval" flowable:delegateExpression="${approvalDelegate}">
 *   <extensionElements>
 *     <flowable:field name="approverRole">
 *       <flowable:string>MANAGER</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="escalationTimeMinutes">
 *       <flowable:string>2880</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="escalationRole">
 *       <flowable:string>DEPT_HEAD</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="autoApproveThreshold">
 *       <flowable:expression>${100.00}</flowable:expression>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 */
@Slf4j
@Component("approvalDelegate")
@RequiredArgsConstructor
public class ApprovalDelegate implements JavaDelegate {

    private final RuntimeService runtimeService;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Executing ApprovalDelegate for process: {}", execution.getProcessInstanceId());

        // Get configuration
        String approverRole = getRequiredVariable(execution, "approverRole");
        String approverUserId = getVariable(execution, "approverUserId", null);
        Boolean escalationEnabled = getVariable(execution, "escalationEnabled", true);
        Integer escalationTimeMinutes = getVariable(execution, "escalationTimeMinutes", 1440);
        String escalationRole = getVariable(execution, "escalationRole", "MANAGER");
        Double autoApproveThreshold = getVariable(execution, "autoApproveThreshold", null);
        Boolean requireComment = getVariable(execution, "requireComment", true);
        Boolean notifyApprover = getVariable(execution, "notifyApprover", true);

        // Check for auto-approval
        if (autoApproveThreshold != null) {
            Double amount = getVariable(execution, "amount", 0.0);
            if (amount <= autoApproveThreshold) {
                log.info("Auto-approving: amount {} is below threshold {}", amount, autoApproveThreshold);
                execution.setVariable("approved", true);
                execution.setVariable("autoApproved", true);
                execution.setVariable("approvalRequired", false);
                return;
            }
        }

        // Set up approval
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueDate = now.plusMinutes(escalationTimeMinutes);

        Map<String, Object> approvalMetadata = new HashMap<>();
        approvalMetadata.put("approvalPending", true);
        approvalMetadata.put("approvalStartTime", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        approvalMetadata.put("approvalDueDate", dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        approvalMetadata.put("approvalEscalationEnabled", escalationEnabled);
        approvalMetadata.put("approvalEscalationRole", escalationRole);
        approvalMetadata.put("approvalRequireComment", requireComment);

        if (approverUserId != null) {
            approvalMetadata.put("approvalAssignedTo", approverUserId);
            approvalMetadata.put("approvalAssignmentType", "user");
        } else {
            approvalMetadata.put("approvalAssignedTo", approverRole);
            approvalMetadata.put("approvalAssignmentType", "role");
        }

        // Set all approval variables
        execution.setVariables(approvalMetadata);

        log.info("Approval setup complete. Assignee: {}, Due: {}",
            approverUserId != null ? approverUserId : approverRole,
            dueDate);

        // Trigger notification if enabled
        if (notifyApprover) {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("notificationType", "approval_required");
            notificationData.put("assignee", approverUserId != null ? approverUserId : approverRole);
            notificationData.put("dueDate", dueDate);
            execution.setVariable("sendApprovalNotification", true);
            execution.setVariable("approvalNotificationData", notificationData);
        }
    }

    /**
     * Complete approval (call this from user task completion)
     */
    public void completeApproval(DelegateExecution execution) {
        Boolean approved = getVariable(execution, "approved", false);
        String comment = getVariable(execution, "approvalComment", "");
        String approver = getVariable(execution, "approver", "");

        LocalDateTime completedAt = LocalDateTime.now();

        Map<String, Object> completionData = new HashMap<>();
        completionData.put("approvalPending", false);
        completionData.put("approvalCompleted", true);
        completionData.put("approvalResult", approved ? "APPROVED" : "REJECTED");
        completionData.put("approvalCompletedBy", approver);
        completionData.put("approvalCompletedAt", completedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        completionData.put("approvalComment", comment);

        execution.setVariables(completionData);

        log.info("Approval completed. Result: {}, Approver: {}",
            approved ? "APPROVED" : "REJECTED", approver);
    }

    /**
     * Handle escalation (called by timer event)
     */
    public void escalateApproval(DelegateExecution execution) {
        String escalationRole = getVariable(execution, "approvalEscalationRole", "MANAGER");
        String originalAssignee = getVariable(execution, "approvalAssignedTo", "");

        log.warn("Escalating approval from {} to {}", originalAssignee, escalationRole);

        Map<String, Object> escalationData = new HashMap<>();
        escalationData.put("approvalEscalated", true);
        escalationData.put("approvalEscalatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        escalationData.put("approvalOriginalAssignee", originalAssignee);
        escalationData.put("approvalAssignedTo", escalationRole);
        escalationData.put("approvalAssignmentType", "role");

        execution.setVariables(escalationData);

        // Trigger escalation notification
        execution.setVariable("sendEscalationNotification", true);
    }

    private String getRequiredVariable(DelegateExecution execution, String variableName) {
        Object value = execution.getVariable(variableName);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Required variable '" + variableName + "' is not set");
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T getVariable(DelegateExecution execution, String variableName, T defaultValue) {
        Object value = execution.getVariable(variableName);
        return value != null ? (T) value : defaultValue;
    }
}
