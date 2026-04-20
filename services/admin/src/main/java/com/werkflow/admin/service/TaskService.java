package com.werkflow.admin.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper service for Flowable task management with authorization and audit logging.
 * Actual Flowable integration is in the engine service.
 */
@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);


    /**
     * Assign a task to a specific user.
     *
     * @param taskId The Flowable task ID
     * @param userId The user ID to assign
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'TASK_MANAGER')")
    public void assignTask(String taskId, String userId) {
        if (taskId == null || taskId.isEmpty()) {
            logger.warn("Cannot assign task: taskId is null or empty");
            return;
        }

        if (userId == null || userId.isEmpty()) {
            logger.warn("Cannot assign task {}: userId is null or empty", taskId);
            return;
        }

        try {
            // In real implementation, this calls Flowable TaskService
            // taskService.setAssignee(taskId, userId);
            logger.info("Task {} assigned to user {}", taskId, userId);
            logAudit("TASK_ASSIGNED", taskId, userId, null);
        } catch (Exception e) {
            logger.error("Failed to assign task {} to user {}", taskId, userId, e);
            throw new RuntimeException("Failed to assign task", e);
        }
    }

    /**
     * Claim a task by current user (sets assignee to current user).
     *
     * @param taskId The Flowable task ID
     * @param userId The user claiming the task
     */
    @PreAuthorize("isAuthenticated()")
    public void claimTask(String taskId, String userId) {
        if (taskId == null || taskId.isEmpty()) {
            logger.warn("Cannot claim task: taskId is null or empty");
            return;
        }

        if (userId == null || userId.isEmpty()) {
            logger.warn("Cannot claim task {}: userId is null or empty", taskId);
            return;
        }

        try {
            // In real implementation, this calls Flowable TaskService
            // taskService.claim(taskId, userId);
            logger.info("Task {} claimed by user {}", taskId, userId);
            logAudit("TASK_CLAIMED", taskId, userId, null);
        } catch (Exception e) {
            logger.error("Failed to claim task {} for user {}", taskId, userId, e);
            throw new RuntimeException("Failed to claim task", e);
        }
    }

    /**
     * Complete a task with variables.
     *
     * @param taskId The Flowable task ID
     * @param variables Process variables to set
     */
    @PreAuthorize("isAuthenticated()")
    public void completeTask(String taskId, Map<String, Object> variables) {
        if (taskId == null || taskId.isEmpty()) {
            logger.warn("Cannot complete task: taskId is null or empty");
            return;
        }

        try {
            // In real implementation, this calls Flowable TaskService
            // taskService.complete(taskId, variables);
            logger.info("Task {} completed with {} variables", taskId, variables != null ? variables.size() : 0);
            logAudit("TASK_COMPLETED", taskId, null, variables);
        } catch (Exception e) {
            logger.error("Failed to complete task {}", taskId, e);
            throw new RuntimeException("Failed to complete task", e);
        }
    }

    /**
     * Delegate a task to another user.
     *
     * @param taskId The Flowable task ID
     * @param delegateUserId The user to delegate to
     * @param delegatingUserId The user delegating the task
     */
    @PreAuthorize("isAuthenticated()")
    public void delegateTask(String taskId, String delegateUserId, String delegatingUserId) {
        if (taskId == null || taskId.isEmpty()) {
            logger.warn("Cannot delegate task: taskId is null or empty");
            return;
        }

        if (delegateUserId == null || delegateUserId.isEmpty()) {
            logger.warn("Cannot delegate task {}: delegateUserId is null or empty", taskId);
            return;
        }

        try {
            // In real implementation, this calls Flowable TaskService
            // taskService.delegateTask(taskId, delegateUserId);
            logger.info("Task {} delegated from {} to {}", taskId, delegatingUserId, delegateUserId);

            Map<String, Object> auditData = new HashMap<>();
            auditData.put("delegatingUserId", delegatingUserId);
            auditData.put("delegateUserId", delegateUserId);
            logAudit("TASK_DELEGATED", taskId, delegateUserId, auditData);
        } catch (Exception e) {
            logger.error("Failed to delegate task {} to user {}", taskId, delegateUserId, e);
            throw new RuntimeException("Failed to delegate task", e);
        }
    }

    /**
     * Unclaim a task (remove assignee).
     *
     * @param taskId The Flowable task ID
     */
    @PreAuthorize("isAuthenticated()")
    public void unclaimTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            logger.warn("Cannot unclaim task: taskId is null or empty");
            return;
        }

        try {
            // In real implementation, this calls Flowable TaskService
            // taskService.setAssignee(taskId, null);
            logger.info("Task {} unclaimed", taskId);
            logAudit("TASK_UNCLAIMED", taskId, null, null);
        } catch (Exception e) {
            logger.error("Failed to unclaim task {}", taskId, e);
            throw new RuntimeException("Failed to unclaim task", e);
        }
    }

    /**
     * Log audit trail for task operations.
     *
     * @param action The action performed
     * @param taskId The task ID
     * @param userId The user ID involved
     * @param variables Additional variables for context
     */
    private void logAudit(String action, String taskId, String userId, Map<String, Object> variables) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("action", action);
        auditEntry.put("taskId", taskId);
        auditEntry.put("userId", userId);
        auditEntry.put("timestamp", System.currentTimeMillis());
        if (variables != null && !variables.isEmpty()) {
            auditEntry.put("variables", variables);
        }

        // In real implementation, save to audit table
        logger.debug("Audit: {}", auditEntry);
    }
}
