package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request object for sending notifications through the notification service.
 * Supports multiple notification types with customizable priority and content format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    /**
     * Type of notification to send
     */
    private NotificationType notificationType;

    /**
     * List of recipient email addresses
     */
    private List<String> recipients;

    /**
     * Template variable context for email content generation
     * Common variables: taskName, processName, assigneeName, deadline, approverName, decision, comments, etc.
     */
    private Map<String, String> context;

    /**
     * Priority level of the notification
     */
    private Priority priority;

    /**
     * Whether to send email as HTML format (true) or plain text (false)
     */
    private boolean sendAsHtml;

    /**
     * Supported notification types for workflow events
     */
    public enum NotificationType {
        TASK_ASSIGNED,
        TASK_COMPLETED,
        TASK_DELEGATED,
        BUDGET_SHORTFALL,
        DEPT_HEAD_REJECTED,
        EXECUTIVE_REJECTED,
        LEGAL_REJECTED,
        CAPEX_COMPLETED
    }

    /**
     * Priority levels for notification handling
     */
    public enum Priority {
        NORMAL,
        HIGH,
        URGENT
    }
}
