package com.werkflow.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Service for building email content from Thymeleaf templates.
 * Handles template variable substitution and generates both HTML and plain text email variants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final TemplateEngine templateEngine;

    @Value("${app.mail.fromName:Werkflow Platform}")
    private String fromName;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    /**
     * Build email content for task assigned notification
     *
     * @param variables Template variables (taskName, processName, assigneeName, deadline, taskId)
     * @return HTML email content
     */
    public String buildTaskAssignedEmail(Map<String, String> variables) {
        log.debug("Building task assigned email with variables: {}", variables);

        Context context = new Context();
        context.setVariable("taskName", variables.getOrDefault("taskName", "Unnamed Task"));
        context.setVariable("processName", variables.getOrDefault("processName", "Workflow Process"));
        context.setVariable("assigneeName", variables.getOrDefault("assigneeName", "User"));
        context.setVariable("deadline", variables.getOrDefault("deadline", "Not specified"));
        context.setVariable("taskId", variables.getOrDefault("taskId", ""));
        context.setVariable("appUrl", appUrl);
        context.setVariable("fromName", fromName);

        String taskDetailsUrl = appUrl + "/tasks/" + variables.getOrDefault("taskId", "");
        context.setVariable("taskDetailsUrl", taskDetailsUrl);

        return templateEngine.process("email/task-assigned", context);
    }

    /**
     * Build email content for task completed notification
     *
     * @param variables Template variables (taskName, processName, decision, comments, approverName, completedBy)
     * @return HTML email content
     */
    public String buildTaskCompletedEmail(Map<String, String> variables) {
        log.debug("Building task completed email with variables: {}", variables);

        Context context = new Context();
        context.setVariable("taskName", variables.getOrDefault("taskName", "Unnamed Task"));
        context.setVariable("processName", variables.getOrDefault("processName", "Workflow Process"));
        context.setVariable("decision", variables.getOrDefault("decision", "Completed"));
        context.setVariable("comments", variables.getOrDefault("comments", "No comments provided"));
        context.setVariable("approverName", variables.getOrDefault("approverName", "User"));
        context.setVariable("completedBy", variables.getOrDefault("completedBy", "User"));
        context.setVariable("processInstanceId", variables.getOrDefault("processInstanceId", ""));
        context.setVariable("appUrl", appUrl);
        context.setVariable("fromName", fromName);

        String processDetailsUrl = appUrl + "/processes/" + variables.getOrDefault("processInstanceId", "");
        context.setVariable("processDetailsUrl", processDetailsUrl);

        boolean isApproved = "approved".equalsIgnoreCase(variables.getOrDefault("decision", ""));
        context.setVariable("isApproved", isApproved);

        return templateEngine.process("email/task-completed", context);
    }

    /**
     * Build email content for task delegated notification
     *
     * @param variables Template variables (taskName, processName, delegatedTo, delegatedBy, reason, taskId)
     * @return HTML email content
     */
    public String buildTaskDelegatedEmail(Map<String, String> variables) {
        log.debug("Building task delegated email with variables: {}", variables);

        Context context = new Context();
        context.setVariable("taskName", variables.getOrDefault("taskName", "Unnamed Task"));
        context.setVariable("processName", variables.getOrDefault("processName", "Workflow Process"));
        context.setVariable("delegatedTo", variables.getOrDefault("delegatedTo", "User"));
        context.setVariable("delegatedBy", variables.getOrDefault("delegatedBy", "User"));
        context.setVariable("reason", variables.getOrDefault("reason", "No reason provided"));
        context.setVariable("taskId", variables.getOrDefault("taskId", ""));
        context.setVariable("appUrl", appUrl);
        context.setVariable("fromName", fromName);

        String acceptUrl = appUrl + "/tasks/" + variables.getOrDefault("taskId", "") + "/accept";
        String rejectUrl = appUrl + "/tasks/" + variables.getOrDefault("taskId", "") + "/reject";
        context.setVariable("acceptUrl", acceptUrl);
        context.setVariable("rejectUrl", rejectUrl);

        return templateEngine.process("email/task-delegated", context);
    }

    /**
     * Build plain text email content for task assigned notification
     *
     * @param variables Template variables
     * @return Plain text email content
     */
    public String buildTaskAssignedPlainText(Map<String, String> variables) {
        String taskName = variables.getOrDefault("taskName", "Unnamed Task");
        String processName = variables.getOrDefault("processName", "Workflow Process");
        String assigneeName = variables.getOrDefault("assigneeName", "User");
        String deadline = variables.getOrDefault("deadline", "Not specified");
        String taskId = variables.getOrDefault("taskId", "");
        String taskDetailsUrl = appUrl + "/tasks/" + taskId;

        return String.format("""
            Dear %s,

            You have been assigned a new task in the %s workflow.

            Task: %s
            Deadline: %s

            To view and complete this task, please visit:
            %s

            Best regards,
            %s
            """, assigneeName, processName, taskName, deadline, taskDetailsUrl, fromName);
    }

    /**
     * Build plain text email content for task completed notification
     *
     * @param variables Template variables
     * @return Plain text email content
     */
    public String buildTaskCompletedPlainText(Map<String, String> variables) {
        String taskName = variables.getOrDefault("taskName", "Unnamed Task");
        String processName = variables.getOrDefault("processName", "Workflow Process");
        String decision = variables.getOrDefault("decision", "Completed");
        String comments = variables.getOrDefault("comments", "No comments provided");
        String completedBy = variables.getOrDefault("completedBy", "User");
        String processInstanceId = variables.getOrDefault("processInstanceId", "");
        String processDetailsUrl = appUrl + "/processes/" + processInstanceId;

        return String.format("""
            A task in the %s workflow has been completed.

            Task: %s
            Decision: %s
            Completed by: %s
            Comments: %s

            To view the process details, please visit:
            %s

            Best regards,
            %s
            """, processName, taskName, decision, completedBy, comments, processDetailsUrl, fromName);
    }

    /**
     * Build plain text email content for task delegated notification
     *
     * @param variables Template variables
     * @return Plain text email content
     */
    public String buildTaskDelegatedPlainText(Map<String, String> variables) {
        String taskName = variables.getOrDefault("taskName", "Unnamed Task");
        String processName = variables.getOrDefault("processName", "Workflow Process");
        String delegatedTo = variables.getOrDefault("delegatedTo", "User");
        String delegatedBy = variables.getOrDefault("delegatedBy", "User");
        String reason = variables.getOrDefault("reason", "No reason provided");
        String taskId = variables.getOrDefault("taskId", "");
        String acceptUrl = appUrl + "/tasks/" + taskId + "/accept";
        String rejectUrl = appUrl + "/tasks/" + taskId + "/reject";

        return String.format("""
            Dear %s,

            A task has been delegated to you in the %s workflow.

            Task: %s
            Delegated by: %s
            Reason: %s

            To accept this delegation, please visit:
            %s

            To reject this delegation, please visit:
            %s

            Best regards,
            %s
            """, delegatedTo, processName, taskName, delegatedBy, reason, acceptUrl, rejectUrl, fromName);
    }
}
