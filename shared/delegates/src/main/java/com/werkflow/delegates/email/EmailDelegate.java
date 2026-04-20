package com.werkflow.delegates.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;

/**
 * Generic Email Delegate for sending emails from workflows
 *
 * Configurable via BPMN process variables:
 * - to: Recipient email address or comma-separated list (required)
 * - cc: CC recipients (optional)
 * - bcc: BCC recipients (optional)
 * - subject: Email subject (required)
 * - body: Email body content (required)
 * - isHtml: Whether body is HTML (default: false)
 * - from: Sender email (optional, uses default from config)
 * - fromName: Sender name (optional)
 * - replyTo: Reply-to address (optional)
 *
 * Example BPMN configuration:
 * <serviceTask id="sendEmail" flowable:delegateExpression="${emailDelegate}">
 *   <extensionElements>
 *     <flowable:field name="to">
 *       <flowable:expression>${employee.email}</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="subject">
 *       <flowable:string>Leave Request Approved</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="body">
 *       <flowable:expression>Your leave request from ${leaveStartDate} to ${leaveEndDate} has been approved.</flowable:expression>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 */
@Slf4j
@Component("emailDelegate")
@RequiredArgsConstructor
public class EmailDelegate implements JavaDelegate {

    private final JavaMailSender mailSender;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Executing EmailDelegate for process: {}", execution.getProcessInstanceId());

        // Get configuration from process variables
        String to = getRequiredVariable(execution, "to");
        String subject = getRequiredVariable(execution, "subject");
        String body = getRequiredVariable(execution, "body");

        String cc = getVariable(execution, "cc", null);
        String bcc = getVariable(execution, "bcc", null);
        Boolean isHtml = getVariable(execution, "isHtml", false);
        String from = getVariable(execution, "from", null);
        String fromName = getVariable(execution, "fromName", null);
        String replyTo = getVariable(execution, "replyTo", null);

        log.debug("Email configuration: to={}, subject={}, isHtml={}", to, subject, isHtml);

        try {
            if (isHtml) {
                sendHtmlEmail(to, cc, bcc, subject, body, from, fromName, replyTo);
            } else {
                sendSimpleEmail(to, cc, bcc, subject, body, from, replyTo);
            }

            log.info("Email sent successfully to: {}", to);

            // Mark as successful
            execution.setVariable("emailSent", true);
            execution.setVariable("emailSentTo", to);

        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage(), e);

            // Store error information
            execution.setVariable("emailSent", false);
            execution.setVariable("emailError", e.getMessage());

            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    private void sendSimpleEmail(String to, String cc, String bcc, String subject,
                                  String body, String from, String replyTo) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(splitEmails(to));
        if (cc != null) message.setCc(splitEmails(cc));
        if (bcc != null) message.setBcc(splitEmails(bcc));
        if (from != null) message.setFrom(from);
        if (replyTo != null) message.setReplyTo(replyTo);

        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }

    private void sendHtmlEmail(String to, String cc, String bcc, String subject,
                                String body, String from, String fromName, String replyTo) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(splitEmails(to));
        if (cc != null) helper.setCc(splitEmails(cc));
        if (bcc != null) helper.setBcc(splitEmails(bcc));

        if (from != null && fromName != null) {
            helper.setFrom(from, fromName);
        } else if (from != null) {
            helper.setFrom(from);
        }

        if (replyTo != null) helper.setReplyTo(replyTo);

        helper.setSubject(subject);
        helper.setText(body, true); // true = isHtml

        mailSender.send(message);
    }

    private String[] splitEmails(String emails) {
        return emails.split("[,;]\\s*");
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
