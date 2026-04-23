package com.werkflow.engine.listener;

import com.werkflow.engine.service.NotificationService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GlobalTaskNotificationListenerTest {

    private NotificationService notificationService;
    private UserEmailResolver emailResolver;
    private RuntimeService runtimeService;
    private RepositoryService repositoryService;
    private HistoryService historyService;
    private GlobalTaskNotificationListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        emailResolver = mock(UserEmailResolver.class);
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        historyService = mock(HistoryService.class);
        listener = new GlobalTaskNotificationListener(
            notificationService, emailResolver, runtimeService, repositoryService, historyService
        );
    }

    @Test
    void onTaskAssigned_sendsEmailToAssignee() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-1");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-1");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-1", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("john.manager")).thenReturn(Optional.of("john.manager@werkflow.local"));

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_ASSIGNED);
        listener.onEvent(event);

        verify(notificationService).sendTaskAssignedNotification(
            eq("task-1"),
            eq("john.manager@werkflow.local"),
            eq("Manager Approval"),
            eq("Leave Request")
        );
    }

    @Test
    void onTaskAssigned_skipsEmail_whenAssigneeEmailNotFound() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-2");
        task.setAssignee("nobody");
        task.setProcessInstanceId("pi-2");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-2", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("nobody")).thenReturn(Optional.empty());

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_ASSIGNED);
        listener.onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void onTaskCompleted_sendsEmailToInitiator_whenAssigneeDiffersFromInitiator() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-3");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-3");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-3", "jane.employee");
        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("jane.employee")).thenReturn(Optional.of("jane.employee@werkflow.local"));

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verify(notificationService).sendTaskCompletedNotification(
            eq("task-3"),
            eq(List.of("jane.employee@werkflow.local")),
            eq("completed"),
            eq(""),
            eq("Manager Approval"),
            eq("Leave Request"),
            eq("john.manager"),
            eq("pi-3")
        );
    }

    @Test
    void onTaskCompleted_skipsNotification_whenAssigneeIsInitiator() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-4");
        task.setName("Submit Request");
        task.setAssignee("jane.employee");
        task.setProcessInstanceId("pi-4");
        task.setProcessDefinitionId("def-1");

        mockProcessInstance("pi-4", "jane.employee");

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void onTaskCompleted_skipsNotification_whenStartUserIdNotFound() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-5");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-5");
        task.setProcessDefinitionId("def-1");

        // Runtime returns null (process not in runtime)
        var runtimeQuery = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceId("pi-5")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        // History also returns null (no record found)
        var historyQuery = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId("pi-5")).thenReturn(historyQuery);
        when(historyQuery.singleResult()).thenReturn(null);

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void onTaskCompleted_usesHistoryFallback_whenProcessInstanceNotInRuntime() {
        TaskEntityImpl task = new TaskEntityImpl();
        task.setId("task-6");
        task.setName("Manager Approval");
        task.setAssignee("john.manager");
        task.setProcessInstanceId("pi-6");
        task.setProcessDefinitionId("def-1");

        // Runtime returns null (process already completed)
        var runtimeQuery = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceId("pi-6")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        // History fallback returns the initiator
        var historyQuery = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hpi =
            mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(hpi.getStartUserId()).thenReturn("jane.employee");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId("pi-6")).thenReturn(historyQuery);
        when(historyQuery.singleResult()).thenReturn(hpi);

        mockProcessDefinition("def-1", "Leave Request");
        when(emailResolver.resolveEmail("jane.employee"))
            .thenReturn(Optional.of("jane.employee@werkflow.local"));

        FlowableEntityEventImpl event = new FlowableEntityEventImpl(task, FlowableEngineEventType.TASK_COMPLETED);
        listener.onEvent(event);

        verify(notificationService).sendTaskCompletedNotification(
            eq("task-6"),
            eq(List.of("jane.employee@werkflow.local")),
            eq("completed"),
            eq(""),
            eq("Manager Approval"),
            eq("Leave Request"),
            eq("john.manager"),
            eq("pi-6")
        );
    }

    @Test
    void isFailOnException_returnsFalse() {
        assertThat(listener.isFailOnException()).isFalse();
    }

    private void mockProcessInstance(String processInstanceId, String startUserId) {
        var query = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getStartUserId()).thenReturn(startUserId);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(processInstanceId)).thenReturn(query);
        when(query.singleResult()).thenReturn(pi);
    }

    private void mockProcessDefinition(String definitionId, String name) {
        ProcessDefinition def = mock(ProcessDefinition.class);
        when(def.getName()).thenReturn(name);
        when(repositoryService.getProcessDefinition(definitionId)).thenReturn(def);
    }
}
