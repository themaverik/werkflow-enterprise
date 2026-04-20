package com.werkflow.engine.service;

import com.werkflow.engine.dto.NotificationRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending email-based workflow notifications.
 * Supports asynchronous execution and automatic retry on failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.mail.from:noreply@werkflow.local}")
    private String mailFrom;

    @Value("${app.mail.fromName:Werkflow Platform}")
    private String fromName;

    @Value("${app.mail.replyTo:support@werkflow.local}")
    private String replyTo;

    /**
     * Send notification when a task is assigned to a user
     *
     * @param taskId Task identifier
     * @param assigneeEmail Email address of the assignee
     * @param taskName Name of the task
     * @param processName Name of the workflow process
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskAssignedNotification(String taskId, String assigneeEmail, String taskName,
                                            String processName) {
        sendTaskAssignedNotification(taskId, assigneeEmail, taskName, processName, "Not specified");
    }

    /**
     * Send notification when a task is assigned to a user with deadline
     *
     * @param taskId Task identifier
     * @param assigneeEmail Email address of the assignee
     * @param taskName Name of the task
     * @param processName Name of the workflow process
     * @param deadline Task deadline
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskAssignedNotification(String taskId, String assigneeEmail, String taskName,
                                            String processName, String deadline) {
        log.info("Sending task assigned notification: taskId={}, assignee={}", taskId, assigneeEmail);

        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("taskId", taskId);
            variables.put("assigneeName", extractNameFromEmail(assigneeEmail));
            variables.put("taskName", taskName);
            variables.put("processName", processName);
            variables.put("deadline", deadline);

            String htmlContent = emailTemplateService.buildTaskAssignedEmail(variables);
            String plainTextContent = emailTemplateService.buildTaskAssignedPlainText(variables);

            sendEmail(
                List.of(assigneeEmail),
                "New Task Assigned: " + taskName,
                htmlContent,
                plainTextContent
            );

            log.info("Task assigned notification sent successfully: taskId={}, assignee={}",
                    taskId, assigneeEmail);
        } catch (MailException e) {
            log.warn("Failed to send task assigned notification due to mail server error: " +
                    "taskId={}, assignee={}. This is non-fatal, workflow will continue.",
                    taskId, assigneeEmail);
            log.debug("Mail error details:", e);
        } catch (Exception e) {
            log.error("Unexpected error sending task assigned notification: taskId={}, assignee={}. " +
                    "This is non-fatal, workflow will continue.",
                    taskId, assigneeEmail, e);
        }
    }

    /**
     * Send notification when a task is completed
     *
     * @param taskId Task identifier
     * @param approverEmails List of email addresses to notify
     * @param decision Approval decision (approved/rejected)
     * @param comments Comments from the approver
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskCompletedNotification(String taskId, List<String> approverEmails,
                                             String decision, String comments) {
        sendTaskCompletedNotification(taskId, approverEmails, decision, comments,
                                     "Unnamed Task", "Workflow Process", "", "");
    }

    /**
     * Send notification when a task is completed with full details
     *
     * @param taskId Task identifier
     * @param approverEmails List of email addresses to notify
     * @param decision Approval decision (approved/rejected)
     * @param comments Comments from the approver
     * @param taskName Name of the task
     * @param processName Name of the workflow process
     * @param completedBy Name of the person who completed the task
     * @param processInstanceId Process instance identifier
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskCompletedNotification(String taskId, List<String> approverEmails,
                                             String decision, String comments,
                                             String taskName, String processName,
                                             String completedBy, String processInstanceId) {
        log.info("Sending task completed notification: taskId={}, recipients={}, decision={}",
                taskId, approverEmails.size(), decision);

        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("taskId", taskId);
            variables.put("taskName", taskName);
            variables.put("processName", processName);
            variables.put("decision", decision);
            variables.put("comments", comments != null ? comments : "No comments provided");
            variables.put("completedBy", completedBy);
            variables.put("processInstanceId", processInstanceId);

            String htmlContent = emailTemplateService.buildTaskCompletedEmail(variables);
            String plainTextContent = emailTemplateService.buildTaskCompletedPlainText(variables);

            String subject = decision.equalsIgnoreCase("approved")
                ? "Task Approved: " + taskName
                : "Task Rejected: " + taskName;

            sendEmail(approverEmails, subject, htmlContent, plainTextContent);

            log.info("Task completed notification sent successfully: taskId={}, recipients={}",
                    taskId, approverEmails.size());
        } catch (MailException e) {
            log.warn("Failed to send task completed notification due to mail server error: " +
                    "taskId={}. This is non-fatal, workflow will continue.", taskId);
            log.debug("Mail error details:", e);
        } catch (Exception e) {
            log.error("Unexpected error sending task completed notification: taskId={}. " +
                    "This is non-fatal, workflow will continue.", taskId, e);
        }
    }

    /**
     * Send notification when a task is delegated to another user
     *
     * @param taskId Task identifier
     * @param delegateEmail Email address of the delegate
     * @param delegatorName Name of the person delegating the task
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskDelegatedNotification(String taskId, String delegateEmail,
                                             String delegatorName) {
        sendTaskDelegatedNotification(taskId, delegateEmail, delegatorName,
                                     "Unnamed Task", "Workflow Process", null);
    }

    /**
     * Send notification when a task is delegated to another user with full details
     *
     * @param taskId Task identifier
     * @param delegateEmail Email address of the delegate
     * @param delegatorName Name of the person delegating the task
     * @param taskName Name of the task
     * @param processName Name of the workflow process
     * @param reason Reason for delegation
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendTaskDelegatedNotification(String taskId, String delegateEmail,
                                             String delegatorName, String taskName,
                                             String processName, String reason) {
        log.info("Sending task delegated notification: taskId={}, delegate={}",
                taskId, delegateEmail);

        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("taskId", taskId);
            variables.put("delegatedTo", extractNameFromEmail(delegateEmail));
            variables.put("delegatedBy", delegatorName);
            variables.put("taskName", taskName);
            variables.put("processName", processName);
            variables.put("reason", reason != null ? reason : "No reason provided");

            String htmlContent = emailTemplateService.buildTaskDelegatedEmail(variables);
            String plainTextContent = emailTemplateService.buildTaskDelegatedPlainText(variables);

            sendEmail(
                List.of(delegateEmail),
                "Task Delegated to You: " + taskName,
                htmlContent,
                plainTextContent
            );

            log.info("Task delegated notification sent successfully: taskId={}, delegate={}",
                    taskId, delegateEmail);
        } catch (MailException e) {
            log.warn("Failed to send task delegated notification due to mail server error: " +
                    "taskId={}, delegate={}. This is non-fatal, workflow will continue.",
                    taskId, delegateEmail);
            log.debug("Mail error details:", e);
        } catch (Exception e) {
            log.error("Unexpected error sending task delegated notification: taskId={}, delegate={}. " +
                    "This is non-fatal, workflow will continue.",
                    taskId, delegateEmail, e);
        }
    }

    /**
     * Send bulk notification to multiple recipients
     *
     * @param request Notification request with recipients and context
     */
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendBulkNotification(NotificationRequest request) {
        log.info("Sending bulk notification: type={}, recipients={}",
                request.getNotificationType(), request.getRecipients().size());

        try {
            String htmlContent = null;
            String plainTextContent = null;
            String subject;

            switch (request.getNotificationType()) {
                case TASK_ASSIGNED:
                    if (request.isSendAsHtml()) {
                        htmlContent = emailTemplateService.buildTaskAssignedEmail(request.getContext());
                    }
                    plainTextContent = emailTemplateService.buildTaskAssignedPlainText(request.getContext());
                    subject = "New Task Assigned: " + request.getContext().getOrDefault("taskName", "Task");
                    break;
                case TASK_COMPLETED:
                    if (request.isSendAsHtml()) {
                        htmlContent = emailTemplateService.buildTaskCompletedEmail(request.getContext());
                    }
                    plainTextContent = emailTemplateService.buildTaskCompletedPlainText(request.getContext());
                    subject = "Task Completed: " + request.getContext().getOrDefault("taskName", "Task");
                    break;
                case TASK_DELEGATED:
                    if (request.isSendAsHtml()) {
                        htmlContent = emailTemplateService.buildTaskDelegatedEmail(request.getContext());
                    }
                    plainTextContent = emailTemplateService.buildTaskDelegatedPlainText(request.getContext());
                    subject = "Task Delegated: " + request.getContext().getOrDefault("taskName", "Task");
                    break;
                default:
                    log.warn("Unsupported notification type for bulk notification: {}",
                            request.getNotificationType());
                    return;
            }

            if (request.isSendAsHtml()) {
                sendEmail(request.getRecipients(), subject, htmlContent, plainTextContent);
            } else {
                sendEmail(request.getRecipients(), subject, null, plainTextContent);
            }

            log.info("Bulk notification sent successfully: type={}, recipients={}",
                    request.getNotificationType(), request.getRecipients().size());
        } catch (MailException e) {
            log.warn("Failed to send bulk notification due to mail server error: " +
                    "type={}. This is non-fatal, workflow will continue.",
                    request.getNotificationType());
            log.debug("Mail error details:", e);
        } catch (Exception e) {
            log.error("Unexpected error sending bulk notification: type={}. " +
                    "This is non-fatal, workflow will continue.",
                    request.getNotificationType(), e);
        }
    }

    /**
     * Internal method to send email with HTML and plain text content
     * Includes graceful error handling to prevent SMTP failures from crashing the application
     */
    private void sendEmail(List<String> recipients, String subject, String htmlContent,
                          String plainTextContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom, fromName);
            helper.setReplyTo(replyTo);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);

            if (htmlContent != null) {
                helper.setText(plainTextContent, htmlContent);
            } else {
                helper.setText(plainTextContent, false);
            }

            mailSender.send(message);
            log.debug("Email sent successfully to {} recipients", recipients.size());
        } catch (MailException e) {
            // SMTP/Mail server errors - log warning but don't crash the application
            log.warn("Failed to send email to recipients due to mail server error: {}. " +
                    "Recipients: {}. This is non-fatal, workflow will continue.",
                    e.getMessage(), recipients);
            log.debug("Mail error details:", e);
        } catch (MessagingException e) {
            // Email formatting/content errors - log warning but don't crash
            log.warn("Failed to send email to recipients due to message formatting error: {}. " +
                    "Recipients: {}. This is non-fatal, workflow will continue.",
                    e.getMessage(), recipients);
            log.debug("Messaging error details:", e);
        } catch (Exception e) {
            // Unexpected errors - log error but still don't crash the workflow
            log.error("Unexpected error while sending email to recipients: {}. " +
                    "This is non-fatal, workflow will continue. Error: {}",
                    recipients, e.getMessage());
            log.debug("Unexpected error details:", e);
        }
    }

    /**
     * Extract display name from email address
     */
    private String extractNameFromEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "User";
        }

        String name = email.split("@")[0];
        name = name.replace(".", " ");
        name = name.replace("_", " ");

        return capitalizeWords(name);
    }

    /**
     * Capitalize the first letter of each word
     */
    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }
}
