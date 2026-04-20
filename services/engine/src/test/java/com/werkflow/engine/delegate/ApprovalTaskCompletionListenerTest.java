package com.werkflow.engine.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApprovalTaskCompletionListener.
 * Validates that approval decisions are correctly captured from user task completions.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTaskCompletionListenerTest {

    @InjectMocks
    private ApprovalTaskCompletionListener listener;

    @Mock
    private DelegateTask delegateTask;

    @Mock
    private DelegateExecution execution;

    private Map<String, Object> taskVariables;

    @BeforeEach
    void setUp() {
        taskVariables = new HashMap<>();
    }

    @Test
    void testManagerApproval_Approved() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("john.manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Budget allocation looks good");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", true);
        verify(delegateTask).setVariable("approvalComments", "Budget allocation looks good");
        verify(delegateTask).setVariable("approvedBy", "john.manager");
        verify(delegateTask).setVariable(eq("approvedAt"), anyLong());
        verify(delegateTask, never()).setVariable(eq("rejectionReason"), anyString());
    }

    @Test
    void testManagerApproval_Rejected() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("jane.manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn("REJECTED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Insufficient justification");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", false);
        verify(delegateTask).setVariable("rejectionReason", "Insufficient justification");
        verify(delegateTask).setVariable("approvedBy", "jane.manager");
        verify(delegateTask).setVariable(eq("approvedAt"), anyLong());
        verify(delegateTask, never()).setVariable(eq("approvalComments"), anyString());
    }

    @Test
    void testVpApproval_Approved() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("vpApproval");
        when(delegateTask.getName()).thenReturn("VP Review");
        when(delegateTask.getAssignee()).thenReturn("vp.finance");
        when(delegateTask.getVariableLocal("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Approved with conditions");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("vpApproved", true);
        verify(delegateTask).setVariable("approvalComments", "Approved with conditions");
        verify(delegateTask).setVariable("approvedBy", "vp.finance");
    }

    @Test
    void testVpApproval_Rejected() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("vpApproval");
        when(delegateTask.getName()).thenReturn("VP Review");
        when(delegateTask.getAssignee()).thenReturn("vp.finance");
        when(delegateTask.getVariableLocal("decision")).thenReturn("REJECTED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Amount exceeds approved budget");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("vpApproved", false);
        verify(delegateTask).setVariable("rejectionReason", "Amount exceeds approved budget");
    }

    @Test
    void testCfoApproval_Approved() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("cfoApproval");
        when(delegateTask.getName()).thenReturn("CFO Review");
        when(delegateTask.getAssignee()).thenReturn("cfo.finance");
        when(delegateTask.getVariableLocal("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Strategic investment approved");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("cfoApproved", true);
        verify(delegateTask).setVariable("approvalComments", "Strategic investment approved");
    }

    @Test
    void testCfoApproval_Rejected() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("cfoApproval");
        when(delegateTask.getName()).thenReturn("CFO Review");
        when(delegateTask.getAssignee()).thenReturn("cfo.finance");
        when(delegateTask.getVariableLocal("decision")).thenReturn("REJECTED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Not aligned with strategic priorities");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("cfoApproved", false);
        verify(delegateTask).setVariable("rejectionReason", "Not aligned with strategic priorities");
    }

    @Test
    void testDecision_CaseInsensitive() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn("approved");
        when(delegateTask.getVariableLocal("comments")).thenReturn("OK");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", true);
    }

    @Test
    void testDecision_WithWhitespace() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn("  REJECTED  ");
        when(delegateTask.getVariableLocal("comments")).thenReturn("  Not enough info  ");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", false);
        verify(delegateTask).setVariable("rejectionReason", "Not enough info");
    }

    @Test
    void testDecision_FallbackToProcessVariable() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn(null);
        when(delegateTask.getVariable("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn(null);
        when(delegateTask.getVariable("comments")).thenReturn("Fallback comments");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", true);
        verify(delegateTask).setVariable("approvalComments", "Fallback comments");
    }

    @Test
    void testMissingDecision_ThrowsException() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getVariableLocal("decision")).thenReturn(null);
        when(delegateTask.getVariable("decision")).thenReturn(null);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            listener.notify(delegateTask);
        });

        assertTrue(exception.getMessage().contains("Failed to capture approval decision"));
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("Approval decision is required"));
    }

    @Test
    void testEmptyDecision_ThrowsException() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getVariableLocal("decision")).thenReturn("");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Some comment");

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            listener.notify(delegateTask);
        });

        assertTrue(exception.getMessage().contains("Failed to capture approval decision"));
    }

    @Test
    void testUnknownTaskType_UsesFallbackVariableName() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("customApprovalTask");
        when(delegateTask.getName()).thenReturn("Custom Approval");
        when(delegateTask.getAssignee()).thenReturn("custom.user");
        when(delegateTask.getVariableLocal("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn("Custom approval");

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("customApprovalTaskApproved", true);
        verify(delegateTask).setVariable("approvalComments", "Custom approval");
    }

    @Test
    void testEmptyComments_StoresEmptyString() {
        // Arrange
        when(delegateTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(delegateTask.getName()).thenReturn("Manager Review");
        when(delegateTask.getAssignee()).thenReturn("manager");
        when(delegateTask.getVariableLocal("decision")).thenReturn("APPROVED");
        when(delegateTask.getVariableLocal("comments")).thenReturn(null);
        when(delegateTask.getVariable("comments")).thenReturn(null);

        // Act
        listener.notify(delegateTask);

        // Assert
        verify(delegateTask).setVariable("managerApproved", true);
        verify(delegateTask).setVariable("approvalComments", "");
    }
}
