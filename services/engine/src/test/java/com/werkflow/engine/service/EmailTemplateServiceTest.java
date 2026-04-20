package com.werkflow.engine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailTemplateService
 */
@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock
    private TemplateEngine templateEngine;

    private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        emailTemplateService = new EmailTemplateService(templateEngine);
        ReflectionTestUtils.setField(emailTemplateService, "fromName", "Werkflow Platform");
        ReflectionTestUtils.setField(emailTemplateService, "appUrl", "http://localhost:3000");
    }

    @Test
    void testBuildTaskAssignedEmail() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-123");
        variables.put("taskName", "Review Budget");
        variables.put("processName", "Budget Approval");
        variables.put("assigneeName", "John Doe");
        variables.put("deadline", "2025-12-31");

        when(templateEngine.process(eq("email/task-assigned"), any(Context.class)))
            .thenReturn("<html>Task Assigned Email</html>");

        String result = emailTemplateService.buildTaskAssignedEmail(variables);

        assertNotNull(result);
        assertEquals("<html>Task Assigned Email</html>", result);
        verify(templateEngine).process(eq("email/task-assigned"), any(Context.class));
    }

    @Test
    void testBuildTaskAssignedEmail_WithMissingVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-456");

        when(templateEngine.process(eq("email/task-assigned"), any(Context.class)))
            .thenReturn("<html>Task Assigned Email</html>");

        String result = emailTemplateService.buildTaskAssignedEmail(variables);

        assertNotNull(result);
        verify(templateEngine).process(eq("email/task-assigned"), argThat(context -> {
            Object taskName = context.getVariable("taskName");
            Object processName = context.getVariable("processName");
            return "Unnamed Task".equals(taskName) && "Workflow Process".equals(processName);
        }));
    }

    @Test
    void testBuildTaskCompletedEmail_Approved() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskName", "Budget Review");
        variables.put("processName", "Annual Budget");
        variables.put("decision", "approved");
        variables.put("comments", "Budget looks reasonable");
        variables.put("approverName", "CFO");
        variables.put("completedBy", "Finance Manager");
        variables.put("processInstanceId", "proc-789");

        when(templateEngine.process(eq("email/task-completed"), any(Context.class)))
            .thenReturn("<html>Task Completed - Approved</html>");

        String result = emailTemplateService.buildTaskCompletedEmail(variables);

        assertNotNull(result);
        assertEquals("<html>Task Completed - Approved</html>", result);
        verify(templateEngine).process(eq("email/task-completed"), argThat(context -> {
            Boolean isApproved = (Boolean) context.getVariable("isApproved");
            return isApproved != null && isApproved;
        }));
    }

    @Test
    void testBuildTaskCompletedEmail_Rejected() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskName", "Purchase Order");
        variables.put("processName", "Procurement");
        variables.put("decision", "rejected");
        variables.put("comments", "Over budget");
        variables.put("completedBy", "Procurement Manager");
        variables.put("processInstanceId", "proc-101");

        when(templateEngine.process(eq("email/task-completed"), any(Context.class)))
            .thenReturn("<html>Task Completed - Rejected</html>");

        String result = emailTemplateService.buildTaskCompletedEmail(variables);

        assertNotNull(result);
        verify(templateEngine).process(eq("email/task-completed"), argThat(context -> {
            Boolean isApproved = (Boolean) context.getVariable("isApproved");
            return isApproved != null && !isApproved;
        }));
    }

    @Test
    void testBuildTaskDelegatedEmail() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-202");
        variables.put("taskName", "Contract Review");
        variables.put("processName", "Contract Management");
        variables.put("delegatedTo", "Backup Manager");
        variables.put("delegatedBy", "Primary Manager");
        variables.put("reason", "On vacation");

        when(templateEngine.process(eq("email/task-delegated"), any(Context.class)))
            .thenReturn("<html>Task Delegated Email</html>");

        String result = emailTemplateService.buildTaskDelegatedEmail(variables);

        assertNotNull(result);
        assertEquals("<html>Task Delegated Email</html>", result);
        verify(templateEngine).process(eq("email/task-delegated"), any(Context.class));
    }

    @Test
    void testBuildTaskAssignedPlainText() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-303");
        variables.put("taskName", "Review Report");
        variables.put("processName", "Quarterly Review");
        variables.put("assigneeName", "Jane Smith");
        variables.put("deadline", "2025-11-30");

        String result = emailTemplateService.buildTaskAssignedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("Dear Jane Smith"));
        assertTrue(result.contains("Review Report"));
        assertTrue(result.contains("Quarterly Review"));
        assertTrue(result.contains("2025-11-30"));
        assertTrue(result.contains("http://localhost:3000/tasks/task-303"));
    }

    @Test
    void testBuildTaskCompletedPlainText() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskName", "Invoice Approval");
        variables.put("processName", "Invoice Processing");
        variables.put("decision", "approved");
        variables.put("comments", "Approved for payment");
        variables.put("completedBy", "Accounts Manager");
        variables.put("processInstanceId", "proc-404");

        String result = emailTemplateService.buildTaskCompletedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("Invoice Approval"));
        assertTrue(result.contains("Invoice Processing"));
        assertTrue(result.contains("approved"));
        assertTrue(result.contains("Approved for payment"));
        assertTrue(result.contains("Accounts Manager"));
        assertTrue(result.contains("http://localhost:3000/processes/proc-404"));
    }

    @Test
    void testBuildTaskDelegatedPlainText() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-505");
        variables.put("taskName", "Sign Document");
        variables.put("processName", "Document Signing");
        variables.put("delegatedTo", "Substitute");
        variables.put("delegatedBy", "Original Signer");
        variables.put("reason", "Out of office");

        String result = emailTemplateService.buildTaskDelegatedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("Dear Substitute"));
        assertTrue(result.contains("Sign Document"));
        assertTrue(result.contains("Document Signing"));
        assertTrue(result.contains("Original Signer"));
        assertTrue(result.contains("Out of office"));
        assertTrue(result.contains("http://localhost:3000/tasks/task-505/accept"));
        assertTrue(result.contains("http://localhost:3000/tasks/task-505/reject"));
    }

    @Test
    void testBuildTaskAssignedPlainText_WithDefaultValues() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-606");

        String result = emailTemplateService.buildTaskAssignedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("Unnamed Task"));
        assertTrue(result.contains("Workflow Process"));
        assertTrue(result.contains("Not specified"));
    }

    @Test
    void testBuildTaskCompletedPlainText_WithNullComments() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskName", "Test Task");
        variables.put("processName", "Test Process");
        variables.put("decision", "completed");
        variables.put("completedBy", "Test User");
        variables.put("processInstanceId", "proc-707");

        String result = emailTemplateService.buildTaskCompletedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("No comments provided"));
    }

    @Test
    void testBuildTaskDelegatedPlainText_WithNoReason() {
        Map<String, String> variables = new HashMap<>();
        variables.put("taskId", "task-808");
        variables.put("taskName", "Review Task");
        variables.put("processName", "Review Process");
        variables.put("delegatedTo", "New User");
        variables.put("delegatedBy", "Old User");

        String result = emailTemplateService.buildTaskDelegatedPlainText(variables);

        assertNotNull(result);
        assertTrue(result.contains("No reason provided"));
    }
}
