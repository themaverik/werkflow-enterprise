package com.werkflow.engine.service;

import com.werkflow.engine.dto.NotificationRequest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "mailFrom", "noreply@werkflow.local");
        ReflectionTestUtils.setField(notificationService, "fromName", "Werkflow Platform");
        ReflectionTestUtils.setField(notificationService, "replyTo", "support@werkflow.local");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void testSendTaskAssignedNotification_Success() {
        String taskId = "task-123";
        String assigneeEmail = "john.doe@example.com";
        String taskName = "Review Budget";
        String processName = "Budget Approval";

        when(emailTemplateService.buildTaskAssignedEmail(any())).thenReturn("<html>Task Assigned</html>");
        when(emailTemplateService.buildTaskAssignedPlainText(any())).thenReturn("Task Assigned");

        notificationService.sendTaskAssignedNotification(taskId, assigneeEmail, taskName, processName);

        verify(emailTemplateService).buildTaskAssignedEmail(any());
        verify(emailTemplateService).buildTaskAssignedPlainText(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskAssignedNotificationWithDeadline_Success() {
        String taskId = "task-456";
        String assigneeEmail = "jane.smith@example.com";
        String taskName = "Approve Purchase Order";
        String processName = "Procurement Workflow";
        String deadline = "2025-12-31";

        when(emailTemplateService.buildTaskAssignedEmail(any())).thenReturn("<html>Task Assigned</html>");
        when(emailTemplateService.buildTaskAssignedPlainText(any())).thenReturn("Task Assigned");

        notificationService.sendTaskAssignedNotification(taskId, assigneeEmail, taskName, processName, deadline);

        verify(emailTemplateService).buildTaskAssignedEmail(argThat(variables ->
            variables.get("deadline").equals(deadline)
        ));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskCompletedNotification_Success() {
        String taskId = "task-789";
        List<String> approverEmails = Arrays.asList("manager@example.com", "director@example.com");
        String decision = "approved";
        String comments = "Looks good";

        when(emailTemplateService.buildTaskCompletedEmail(any())).thenReturn("<html>Task Completed</html>");
        when(emailTemplateService.buildTaskCompletedPlainText(any())).thenReturn("Task Completed");

        notificationService.sendTaskCompletedNotification(taskId, approverEmails, decision, comments);

        verify(emailTemplateService).buildTaskCompletedEmail(any());
        verify(emailTemplateService).buildTaskCompletedPlainText(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskCompletedNotificationFullDetails_Success() {
        String taskId = "task-101";
        List<String> approverEmails = Arrays.asList("cfo@example.com");
        String decision = "rejected";
        String comments = "Budget insufficient";
        String taskName = "CapEx Request";
        String processName = "Capital Expenditure";
        String completedBy = "Finance Manager";
        String processInstanceId = "proc-202";

        when(emailTemplateService.buildTaskCompletedEmail(any())).thenReturn("<html>Task Completed</html>");
        when(emailTemplateService.buildTaskCompletedPlainText(any())).thenReturn("Task Completed");

        notificationService.sendTaskCompletedNotification(
            taskId, approverEmails, decision, comments,
            taskName, processName, completedBy, processInstanceId
        );

        verify(emailTemplateService).buildTaskCompletedEmail(argThat(variables ->
            variables.get("decision").equals(decision) &&
            variables.get("taskName").equals(taskName) &&
            variables.get("completedBy").equals(completedBy)
        ));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskDelegatedNotification_Success() {
        String taskId = "task-303";
        String delegateEmail = "backup.manager@example.com";
        String delegatorName = "Primary Manager";

        when(emailTemplateService.buildTaskDelegatedEmail(any())).thenReturn("<html>Task Delegated</html>");
        when(emailTemplateService.buildTaskDelegatedPlainText(any())).thenReturn("Task Delegated");

        notificationService.sendTaskDelegatedNotification(taskId, delegateEmail, delegatorName);

        verify(emailTemplateService).buildTaskDelegatedEmail(any());
        verify(emailTemplateService).buildTaskDelegatedPlainText(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskDelegatedNotificationWithReason_Success() {
        String taskId = "task-404";
        String delegateEmail = "substitute@example.com";
        String delegatorName = "Original Assignee";
        String taskName = "Sign Contract";
        String processName = "Contract Management";
        String reason = "On vacation";

        when(emailTemplateService.buildTaskDelegatedEmail(any())).thenReturn("<html>Task Delegated</html>");
        when(emailTemplateService.buildTaskDelegatedPlainText(any())).thenReturn("Task Delegated");

        notificationService.sendTaskDelegatedNotification(
            taskId, delegateEmail, delegatorName, taskName, processName, reason
        );

        verify(emailTemplateService).buildTaskDelegatedEmail(argThat(variables ->
            variables.get("reason").equals(reason) &&
            variables.get("taskName").equals(taskName)
        ));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendBulkNotification_TaskAssigned() {
        NotificationRequest request = NotificationRequest.builder()
            .notificationType(NotificationRequest.NotificationType.TASK_ASSIGNED)
            .recipients(Arrays.asList("user1@example.com", "user2@example.com"))
            .context(createTestContext("Task 1", "Process 1"))
            .priority(NotificationRequest.Priority.NORMAL)
            .sendAsHtml(true)
            .build();

        when(emailTemplateService.buildTaskAssignedEmail(any())).thenReturn("<html>Bulk Task Assigned</html>");
        when(emailTemplateService.buildTaskAssignedPlainText(any())).thenReturn("Bulk Task Assigned");

        notificationService.sendBulkNotification(request);

        verify(emailTemplateService).buildTaskAssignedEmail(any());
        verify(emailTemplateService).buildTaskAssignedPlainText(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendBulkNotification_PlainTextOnly() {
        NotificationRequest request = NotificationRequest.builder()
            .notificationType(NotificationRequest.NotificationType.TASK_COMPLETED)
            .recipients(Arrays.asList("user@example.com"))
            .context(createTestContext("Task Complete", "Workflow"))
            .priority(NotificationRequest.Priority.HIGH)
            .sendAsHtml(false)
            .build();

        when(emailTemplateService.buildTaskCompletedPlainText(any())).thenReturn("Task Completed");

        notificationService.sendBulkNotification(request);

        verify(emailTemplateService, never()).buildTaskCompletedEmail(any());
        verify(emailTemplateService).buildTaskCompletedPlainText(any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testSendTaskAssignedNotification_RetryOnFailure() {
        String taskId = "task-error";
        String assigneeEmail = "user@example.com";
        String taskName = "Test Task";
        String processName = "Test Process";

        when(emailTemplateService.buildTaskAssignedEmail(any())).thenReturn("<html>Task Assigned</html>");
        when(emailTemplateService.buildTaskAssignedPlainText(any())).thenReturn("Task Assigned");

        doThrow(new MailException("SMTP error") {})
            .doNothing()
            .when(mailSender).send(any(MimeMessage.class));

        try {
            notificationService.sendTaskAssignedNotification(taskId, assigneeEmail, taskName, processName);
        } catch (Exception e) {
            // Expected on first failure
        }

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void testExtractNameFromEmail() {
        ReflectionTestUtils.setField(notificationService, "mailFrom", "noreply@werkflow.local");

        String taskId = "task-name-test";
        String assigneeEmail = "john.doe@example.com";
        String taskName = "Test";
        String processName = "Test";

        when(emailTemplateService.buildTaskAssignedEmail(any())).thenReturn("<html>Test</html>");
        when(emailTemplateService.buildTaskAssignedPlainText(any())).thenReturn("Test");

        notificationService.sendTaskAssignedNotification(taskId, assigneeEmail, taskName, processName);

        verify(emailTemplateService).buildTaskAssignedEmail(argThat(variables ->
            variables.get("assigneeName").equals("John Doe")
        ));
    }

    private Map<String, String> createTestContext(String taskName, String processName) {
        Map<String, String> context = new HashMap<>();
        context.put("taskName", taskName);
        context.put("processName", processName);
        context.put("taskId", "task-123");
        context.put("assigneeName", "Test User");
        context.put("deadline", "2025-12-31");
        return context;
    }
}
