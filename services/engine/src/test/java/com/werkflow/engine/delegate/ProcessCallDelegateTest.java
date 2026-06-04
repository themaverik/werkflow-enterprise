package com.werkflow.engine.delegate;

import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessCallDelegateTest {

    @InjectMocks
    private ProcessCallDelegate delegate;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private DelegateExecution execution;

    @Mock
    private FlowElement flowElement;

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    @BeforeEach
    void setUp() {
        when(execution.getCurrentFlowElement()).thenReturn(flowElement);
    }

    @Test
    void notify_whenTriggerProcessIsNull_doesNothing() {
        when(flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess")).thenReturn(null);

        delegate.notify(execution);

        verify(runtimeService, never()).startProcessInstanceByKey(any(), any(Map.class));
    }

    @Test
    void notify_whenTriggerProcessIsBlank_doesNothing() {
        when(flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess")).thenReturn("  ");

        delegate.notify(execution);

        verify(runtimeService, never()).startProcessInstanceByKey(any(), any(Map.class));
    }

    @Test
    void notify_whenValidKey_startsProcessWithVariables() {
        Map<String, Object> vars = Map.of("amount", 5000, "requesterId", "user1");
        when(flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess"))
            .thenReturn("procurement-approval-process");
        when(execution.getVariables()).thenReturn(vars);

        delegate.notify(execution);

        verify(runtimeService).startProcessInstanceByKeyAndTenantId(
            eq("procurement-approval-process"), eq(vars), isNull());
    }

    @Test
    void notify_whenProcessNotFound_logsWarningAndDoesNotThrow() {
        when(flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess"))
            .thenReturn("nonexistent-process");
        when(execution.getVariables()).thenReturn(new HashMap<>());
        doThrow(new FlowableObjectNotFoundException("not found"))
            .when(runtimeService).startProcessInstanceByKeyAndTenantId(eq("nonexistent-process"), any(Map.class), any());

        // Should not throw — parent process continues
        delegate.notify(execution);
    }

    @Test
    void notify_whenUnexpectedExceptionOccurs_rethrows() {
        when(flowElement.getAttributeValue(FLOWABLE_NS, "triggerProcess"))
            .thenReturn("some-process");
        when(execution.getVariables()).thenReturn(new HashMap<>());
        doThrow(new RuntimeException("unexpected"))
            .when(runtimeService).startProcessInstanceByKeyAndTenantId(eq("some-process"), any(Map.class), any());

        assertThrows(RuntimeException.class, () -> delegate.notify(execution));
    }
}
