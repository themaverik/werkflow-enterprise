package com.werkflow.engine.delegate;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Task listener that automatically assigns approval tasks based on routing logic.
 * Triggered when an approval task is created in a workflow.
 *
 * NOTE: Not registered as a Spring bean — superseded by DOA-based candidateGroup routing in BPMNs.
 */
public class TaskAssignmentDelegate implements TaskListener {

    private static final Logger logger = LoggerFactory.getLogger(TaskAssignmentDelegate.class);

    private static final long serialVersionUID = 1L;

    /**
     * Called when task is created. Routes to appropriate approver.
     *
     * @param delegateTask The Flowable task being created
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        try {
            String taskName = delegateTask.getName();
            String taskDefinitionKey = delegateTask.getTaskDefinitionKey();

            logger.debug("Assigning task: {} ({})", taskName, taskDefinitionKey);

            // Determine routing based on task type
            if (isCapExApprovalTask(taskDefinitionKey)) {
                routeCapExApprovalTask(delegateTask);
            } else if (isProcurementApprovalTask(taskDefinitionKey)) {
                routeProcurementApprovalTask(delegateTask);
            } else if (isAssetTransferApprovalTask(taskDefinitionKey)) {
                routeAssetTransferApprovalTask(delegateTask);
            } else {
                logger.debug("Task {} has no specific routing rule, using default assignment", taskName);
            }
        } catch (Exception e) {
            logger.error("Error assigning task {}", delegateTask.getName(), e);
            // Don't throw - let workflow continue with default assignment
        }
    }

    /**
     * Route a CapEx approval task.
     */
    private void routeCapExApprovalTask(DelegateTask task) {
        BigDecimal requestAmount = getRequestAmount(task);
        String requestorDepartment = getRequestorDepartment(task);
        Integer currentApproverDoaLevel = getCurrentApproverDoaLevel(task);

        String candidateGroup = determineCandidateGroup(requestAmount);

        task.addCandidateGroup(candidateGroup);
        logger.info(
            "CapEx approval task routed to group '{}' for amount ${}",
            candidateGroup, requestAmount
        );
    }

    /**
     * Route a Procurement approval task.
     */
    private void routeProcurementApprovalTask(DelegateTask task) {
        BigDecimal requestAmount = getRequestAmount(task);
        String vendorId = (String) task.getVariable("vendorId");

        String candidateGroup = determineCandidateGroup(requestAmount);

        task.addCandidateGroup(candidateGroup);
        logger.info(
            "Procurement approval task routed to group '{}' for vendor {}, amount ${}",
            candidateGroup, vendorId, requestAmount
        );
    }

    /**
     * Route an Asset Transfer approval task.
     */
    private void routeAssetTransferApprovalTask(DelegateTask task) {
        BigDecimal assetValue = getAssetValue(task);
        String fromHub = (String) task.getVariable("fromHub");
        String toHub = (String) task.getVariable("toHub");

        String candidateGroup = determineCandidateGroup(assetValue);

        task.addCandidateGroup(candidateGroup);
        logger.info(
            "Asset transfer approval task routed to group '{}' from {} to {}, value ${}",
            candidateGroup, fromHub, toHub, assetValue
        );
    }

    /**
     * Determine candidate group based on amount.
     */
    private String determineCandidateGroup(BigDecimal amount) {
        if (amount == null) {
            return "finance_approvers";
        }

        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return "department_managers";
        } else if (amount.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return "department_heads";
        } else if (amount.compareTo(BigDecimal.valueOf(100000)) < 0) {
            return "finance_approvers";
        } else {
            return "executive_approvers";
        }
    }

    /**
     * Extract request amount from process variables.
     */
    private BigDecimal getRequestAmount(DelegateTask task) {
        Object amount = task.getVariable("amount");
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        if (amount instanceof BigDecimal) {
            return (BigDecimal) amount;
        } else if (amount instanceof Number) {
            return new BigDecimal(amount.toString());
        } else {
            return new BigDecimal(amount.toString());
        }
    }

    /**
     * Extract asset value from process variables.
     */
    private BigDecimal getAssetValue(DelegateTask task) {
        Object value = task.getVariable("assetValue");
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof Number) {
            return new BigDecimal(value.toString());
        } else {
            return new BigDecimal(value.toString());
        }
    }

    /**
     * Extract requestor department from process variables.
     */
    private String getRequestorDepartment(DelegateTask task) {
        Object department = task.getVariable("requestorDepartment");
        return department != null ? department.toString() : "Unknown";
    }

    /**
     * Extract current approver DOA level from process variables.
     */
    private Integer getCurrentApproverDoaLevel(DelegateTask task) {
        Object level = task.getVariable("currentApproverDoaLevel");
        if (level == null) {
            return null;
        }

        if (level instanceof Integer) {
            return (Integer) level;
        } else if (level instanceof Number) {
            return ((Number) level).intValue();
        } else {
            try {
                return Integer.parseInt(level.toString());
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse DOA level: {}", level);
                return null;
            }
        }
    }

    /**
     * Check if task is a CapEx approval task.
     */
    private boolean isCapExApprovalTask(String taskDefinitionKey) {
        return taskDefinitionKey != null && (
            taskDefinitionKey.contains("capex") ||
            taskDefinitionKey.contains("CapEx") ||
            taskDefinitionKey.contains("capExApproval")
        );
    }

    /**
     * Check if task is a Procurement approval task.
     */
    private boolean isProcurementApprovalTask(String taskDefinitionKey) {
        return taskDefinitionKey != null && (
            taskDefinitionKey.contains("procurement") ||
            taskDefinitionKey.contains("Procurement") ||
            taskDefinitionKey.contains("procurementApproval") ||
            taskDefinitionKey.contains("purchaseOrder") ||
            taskDefinitionKey.contains("PurchaseOrder")
        );
    }

    /**
     * Check if task is an Asset Transfer approval task.
     */
    private boolean isAssetTransferApprovalTask(String taskDefinitionKey) {
        return taskDefinitionKey != null && (
            taskDefinitionKey.contains("assetTransfer") ||
            taskDefinitionKey.contains("AssetTransfer") ||
            taskDefinitionKey.contains("asset") && taskDefinitionKey.contains("transfer")
        );
    }
}
