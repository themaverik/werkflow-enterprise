package com.werkflow.delegates.notification;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic Notification Delegate for multi-channel notifications
 *
 * Configurable via BPMN process variables:
 * - recipients: Comma-separated user IDs or emails (required)
 * - channels: Comma-separated channels (email, sms, push, in-app) - default: email
 * - message: Notification message (required)
 * - subject: Notification subject (optional, for email)
 * - priority: Notification priority (low, normal, high, urgent) - default: normal
 * - actionUrl: URL for action button (optional)
 * - actionLabel: Label for action button (optional)
 *
 * Example BPMN configuration:
 * <serviceTask id="notifyManager" flowable:delegateExpression="${notificationDelegate}">
 *   <extensionElements>
 *     <flowable:field name="recipients">
 *       <flowable:expression>${managerEmail}</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="channels">
 *       <flowable:string>email,in-app</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="message">
 *       <flowable:expression>New leave request from ${employeeName} requires your approval.</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="priority">
 *       <flowable:string>high</flowable:string>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 */
@Slf4j
@Component("notificationDelegate")
public class NotificationDelegate implements JavaDelegate {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Executing NotificationDelegate for process: {}", execution.getProcessInstanceId());

        // Get configuration from process variables
        String recipients = getRequiredVariable(execution, "recipients");
        String message = getRequiredVariable(execution, "message");

        String channels = getVariable(execution, "channels", "email");
        String subject = getVariable(execution, "subject", "Workflow Notification");
        String priority = getVariable(execution, "priority", "normal");
        String actionUrl = getVariable(execution, "actionUrl", null);
        String actionLabel = getVariable(execution, "actionLabel", "View Details");

        log.debug("Notification configuration: recipients={}, channels={}, priority={}",
            recipients, channels, priority);

        List<String> channelList = parseChannels(channels);
        List<String> recipientList = parseRecipients(recipients);

        List<String> sentChannels = new ArrayList<>();
        List<String> failedChannels = new ArrayList<>();

        // Send to each channel
        for (String channel : channelList) {
            try {
                switch (channel.toLowerCase()) {
                    case "email":
                        sendEmailNotification(recipientList, subject, message, actionUrl, actionLabel);
                        sentChannels.add("email");
                        break;
                    case "sms":
                        sendSmsNotification(recipientList, message);
                        sentChannels.add("sms");
                        break;
                    case "push":
                        sendPushNotification(recipientList, subject, message, actionUrl);
                        sentChannels.add("push");
                        break;
                    case "in-app":
                        sendInAppNotification(recipientList, subject, message, priority, actionUrl, actionLabel);
                        sentChannels.add("in-app");
                        break;
                    default:
                        log.warn("Unknown notification channel: {}", channel);
                }
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channel, e.getMessage());
                failedChannels.add(channel);
            }
        }

        log.info("Notifications sent via channels: {}", sentChannels);

        // Store results
        execution.setVariable("notificationSent", !sentChannels.isEmpty());
        execution.setVariable("notificationChannels", sentChannels);
        execution.setVariable("notificationFailedChannels", failedChannels);
        execution.setVariable("notificationRecipients", recipientList.size());
    }

    private void sendEmailNotification(List<String> recipients, String subject, String message,
                                        String actionUrl, String actionLabel) {
        if (mailSender == null) {
            log.warn("JavaMailSender not configured, email notification will be logged only");
            log.info("Email would be sent to {} recipients with subject: {}", recipients.size(), subject);
            return;
        }

        String emailBody = buildEmailBody(message, actionUrl, actionLabel);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(recipients.toArray(new String[0]));
        mailMessage.setSubject(subject);
        mailMessage.setText(emailBody);

        mailSender.send(mailMessage);

        log.debug("Email notification sent to {} recipients", recipients.size());
    }

    private void sendSmsNotification(List<String> recipients, String message) {
        // TODO: Integrate with SMS provider (Twilio, AWS SNS, etc.)
        log.info("SMS notification would be sent to {} recipients: {}", recipients.size(), message);
        // This is a placeholder - actual SMS integration would go here
    }

    private void sendPushNotification(List<String> recipients, String title, String message, String actionUrl) {
        // TODO: Integrate with push notification service (Firebase, OneSignal, etc.)
        log.info("Push notification would be sent to {} recipients: {}", recipients.size(), message);
        // This is a placeholder - actual push notification integration would go here
    }

    private void sendInAppNotification(List<String> recipients, String title, String message,
                                        String priority, String actionUrl, String actionLabel) {
        // TODO: Store in-app notification in database for user to see in UI
        log.info("In-app notification would be created for {} recipients: {}", recipients.size(), message);
        // This is a placeholder - actual in-app notification storage would go here
    }

    private String buildEmailBody(String message, String actionUrl, String actionLabel) {
        StringBuilder body = new StringBuilder(message);

        if (actionUrl != null) {
            body.append("\n\n").append(actionLabel).append(": ").append(actionUrl);
        }

        return body.toString();
    }

    private List<String> parseChannels(String channels) {
        List<String> channelList = new ArrayList<>();
        for (String channel : channels.split(",")) {
            channelList.add(channel.trim());
        }
        return channelList;
    }

    private List<String> parseRecipients(String recipients) {
        List<String> recipientList = new ArrayList<>();
        for (String recipient : recipients.split(",")) {
            recipientList.add(recipient.trim());
        }
        return recipientList;
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
