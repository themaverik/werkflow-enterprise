package com.werkflow.engine.delegate;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.UserProfileDto;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetOwningDepartmentDelegateTest {

    @Mock AdminServiceClient adminServiceClient;
    @Mock DelegateExecution execution;
    @InjectMocks SetOwningDepartmentDelegate delegate;

    @Test
    void setsOwningDepartmentFromErpProfile() {
        when(execution.getVariable("submitterId")).thenReturn("user-42");
        when(execution.getTenantId()).thenReturn("acme");
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(adminServiceClient.getUserProfile("user-42", "acme"))
                .thenReturn(new UserProfileDto("user-42", "acme", null, "IT"));

        delegate.execute(execution);

        verify(execution).setVariable("owningDepartment", "IT");
    }

    @Test
    void fallsBackToInitiatorVariableWhenSubmitterIdAbsent() {
        when(execution.getVariable("submitterId")).thenReturn(null);
        when(execution.getVariable("initiator")).thenReturn("user-99");
        when(execution.getTenantId()).thenReturn("acme");
        when(execution.getProcessInstanceId()).thenReturn("proc-2");
        when(adminServiceClient.getUserProfile("user-99", "acme"))
                .thenReturn(new UserProfileDto("user-99", "acme", null, "FINANCE"));

        delegate.execute(execution);

        verify(execution).setVariable("owningDepartment", "FINANCE");
    }

    @Test
    void doesNotOverwriteWhenErpReturnsNullDept() {
        when(execution.getVariable("submitterId")).thenReturn("user-1");
        when(execution.getTenantId()).thenReturn("acme");
        when(execution.getProcessInstanceId()).thenReturn("proc-3");
        when(adminServiceClient.getUserProfile("user-1", "acme"))
                .thenReturn(new UserProfileDto("user-1", "acme", null, null));

        delegate.execute(execution);

        verify(execution, never()).setVariable(eq("owningDepartment"), any());
    }

    @Test
    void doesNotThrowWhenErpUnavailable() {
        when(execution.getVariable("submitterId")).thenReturn("user-1");
        when(execution.getTenantId()).thenReturn("acme");
        when(execution.getProcessInstanceId()).thenReturn("proc-4");
        when(adminServiceClient.getUserProfile("user-1", "acme"))
                .thenThrow(new RuntimeException("ERP down"));

        // must not propagate the exception
        delegate.execute(execution);

        verify(execution, never()).setVariable(eq("owningDepartment"), any());
    }

    @Test
    void doesNothingWhenNoSubmitterResolvable() {
        when(execution.getVariable("submitterId")).thenReturn(null);
        when(execution.getVariable("initiator")).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("proc-5");

        delegate.execute(execution);

        verify(adminServiceClient, never()).getUserProfile(any(), any());
        verify(execution, never()).setVariable(any(), any());
    }
}
