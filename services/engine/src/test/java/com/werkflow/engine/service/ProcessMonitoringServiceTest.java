package com.werkflow.engine.service;

import com.werkflow.engine.dto.*;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.exception.UnauthorizedTaskAccessException;
import com.werkflow.engine.util.ProcessMonitoringUtil;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for ProcessMonitoringService
 */
@ExtendWith(MockitoExtension.class)
class ProcessMonitoringServiceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private TaskService taskService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProcessMonitoringUtil monitoringUtil;

    @Mock
    private HistoricProcessInstanceQuery processInstanceQuery;

    @Mock
    private HistoricTaskInstanceQuery taskInstanceQuery;

    @Mock
    private HistoricActivityInstanceQuery activityInstanceQuery;

    @Mock
    private HistoricVariableInstanceQuery variableInstanceQuery;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private ProcessDefinitionQuery processDefinitionQuery;

    @InjectMocks
    private ProcessMonitoringService processMonitoringService;

    private JwtUserContext userContext;
    private HistoricProcessInstance mockProcessInstance;
    private static final String PROCESS_INSTANCE_ID = "process-123";
    private static final String BUSINESS_KEY = "PR-2025-00042";

    @BeforeEach
    void setUp() {
        userContext = JwtUserContext.builder()
                .userId("john.doe")
                .email("john.doe@example.com")
                .fullName("John Doe")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF", "APPROVERS"))
                .roles(List.of("USER", "APPROVER"))
                .doaLevel(2)
                .build();
    }

    private HistoricProcessInstance createMockProcessInstance(String startUserId) {
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        lenient().when(instance.getId()).thenReturn(PROCESS_INSTANCE_ID);
        lenient().when(instance.getBusinessKey()).thenReturn(BUSINESS_KEY);
        lenient().when(instance.getProcessDefinitionKey()).thenReturn("purchase-requisition-approval");
        lenient().when(instance.getProcessDefinitionId()).thenReturn("purchase-requisition-approval:1:abc123");
        lenient().when(instance.getStartUserId()).thenReturn(startUserId);
        lenient().when(instance.getStartTime()).thenReturn(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        lenient().when(instance.getEndTime()).thenReturn(null);
        return instance;
    }

    @Test
    void getProcessInstanceDetails_shouldReturnDetails_whenProcessExists() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(Collections.emptyList());
        when(taskQuery.count()).thenReturn(0L);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.finished()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.count()).thenReturn(3L);

        when(monitoringUtil.getCurrentTaskInfo(anyList())).thenReturn(Collections.emptyMap());

        ProcessDefinition mockDefinition = mock(ProcessDefinition.class);
        when(mockDefinition.getName()).thenReturn("Purchase Requisition Approval");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(mockDefinition);

        // Act
        ProcessInstanceDTO result = processMonitoringService.getProcessInstanceDetails(
                PROCESS_INSTANCE_ID, false, userContext);

        // Assert
        assertNotNull(result);
        assertEquals(PROCESS_INSTANCE_ID, result.getId());
        assertEquals(BUSINESS_KEY, result.getBusinessKey());
        assertEquals("purchase-requisition-approval", result.getProcessDefinitionKey());
        assertEquals("RUNNING", result.getStatus());
        assertEquals("john.doe", result.getInitiatorUsername());
        assertEquals(0, result.getActiveTaskCount());
        assertEquals(3, result.getCompletedTaskCount());

        verify(processInstanceQuery).processInstanceId(PROCESS_INSTANCE_ID);
    }

    @Test
    void getProcessInstanceDetails_shouldIncludeVariables_whenRequested() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(Collections.emptyList());
        when(taskQuery.count()).thenReturn(0L);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.finished()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.count()).thenReturn(0L);

        when(monitoringUtil.getCurrentTaskInfo(anyList())).thenReturn(Collections.emptyMap());

        // Mock variables
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableInstanceQuery);
        when(variableInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(variableInstanceQuery);
        List<HistoricVariableInstance> mockVariables = new ArrayList<>();
        when(variableInstanceQuery.list()).thenReturn(mockVariables);

        Map<String, Object> extractedVars = Map.of("amount", 5000, "department", "Finance");
        when(monitoringUtil.extractVariablesForDTO(mockVariables)).thenReturn(extractedVars);

        ProcessDefinition mockDefinition = mock(ProcessDefinition.class);
        when(mockDefinition.getName()).thenReturn("Purchase Requisition Approval");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(mockDefinition);

        // Act
        ProcessInstanceDTO result = processMonitoringService.getProcessInstanceDetails(
                PROCESS_INSTANCE_ID, true, userContext);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getVariables());
        assertEquals(2, result.getVariables().size());
        verify(monitoringUtil).extractVariablesForDTO(mockVariables);
    }

    @Test
    void getProcessInstanceDetails_shouldThrowException_whenProcessNotFound() {
        // Arrange
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);

        // Act & Assert
        assertThrows(ProcessNotFoundException.class, () ->
                processMonitoringService.getProcessInstanceDetails(PROCESS_INSTANCE_ID, false, userContext));
    }

    @Test
    void getProcessInstanceDetails_shouldThrowException_whenUserNotAuthorized() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("another.user");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.taskInvolvedUser("john.doe")).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.count()).thenReturn(0L);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("john.doe")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenReturn(taskQuery);

        // Act & Assert
        assertThrows(UnauthorizedTaskAccessException.class, () ->
                processMonitoringService.getProcessInstanceDetails(PROCESS_INSTANCE_ID, false, userContext));
    }

    @Test
    void getProcessInstanceTasks_shouldReturnActiveTasks() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        Task mockTask = mock(Task.class);
        when(mockTask.getId()).thenReturn("task-1");
        when(mockTask.getName()).thenReturn("Manager Approval");
        when(mockTask.getTaskDefinitionKey()).thenReturn("managerApproval");
        when(mockTask.getAssignee()).thenReturn("jane.smith");
        when(mockTask.getCreateTime()).thenReturn(Date.from(Instant.now()));
        when(mockTask.getPriority()).thenReturn(50);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(mockTask));

        lenient().when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        lenient().when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        lenient().when(taskInstanceQuery.taskInvolvedUser(anyString())).thenReturn(taskInstanceQuery);
        lenient().when(taskInstanceQuery.count()).thenReturn(1L);

        // Act
        List<ProcessTaskHistoryDTO> result = processMonitoringService.getProcessInstanceTasks(
                PROCESS_INSTANCE_ID, false, "ACTIVE", userContext);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task-1", result.get(0).getTaskId());
        assertEquals("Manager Approval", result.get(0).getName());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals("PENDING", result.get(0).getOutcome());
    }

    @Test
    void getProcessInstanceTasks_shouldReturnCompletedTasks() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        HistoricTaskInstance mockHistoricTask = mock(HistoricTaskInstance.class);
        when(mockHistoricTask.getId()).thenReturn("task-1");
        when(mockHistoricTask.getName()).thenReturn("Finance Review");
        when(mockHistoricTask.getTaskDefinitionKey()).thenReturn("financeReview");
        when(mockHistoricTask.getAssignee()).thenReturn("john.doe");
        when(mockHistoricTask.getCreateTime()).thenReturn(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        when(mockHistoricTask.getEndTime()).thenReturn(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        when(mockHistoricTask.getPriority()).thenReturn(50);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.finished()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.asc()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.list()).thenReturn(List.of(mockHistoricTask));
        lenient().when(taskInstanceQuery.taskInvolvedUser(anyString())).thenReturn(taskInstanceQuery);
        lenient().when(taskInstanceQuery.count()).thenReturn(1L);

        lenient().when(taskService.createTaskQuery()).thenReturn(taskQuery);
        lenient().when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        lenient().when(taskQuery.list()).thenReturn(Collections.emptyList());

        when(monitoringUtil.determineTaskOutcome(mockHistoricTask)).thenReturn("APPROVED");
        when(monitoringUtil.calculateDuration(any(Instant.class), any(Instant.class))).thenReturn(30L);

        // Act
        List<ProcessTaskHistoryDTO> result = processMonitoringService.getProcessInstanceTasks(
                PROCESS_INSTANCE_ID, true, "COMPLETED", userContext);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task-1", result.get(0).getTaskId());
        assertEquals("Finance Review", result.get(0).getName());
        assertEquals("COMPLETED", result.get(0).getStatus());
        assertEquals("APPROVED", result.get(0).getOutcome());
        assertEquals(30L, result.get(0).getDurationInMinutes());
    }

    @Test
    void getProcessInstanceHistory_shouldReturnPaginatedEvents() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityInstanceQuery);
        when(activityInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(activityInstanceQuery);
        when(activityInstanceQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(activityInstanceQuery);
        when(activityInstanceQuery.asc()).thenReturn(activityInstanceQuery);
        when(activityInstanceQuery.list()).thenReturn(Collections.emptyList());

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.asc()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.list()).thenReturn(Collections.emptyList());
        lenient().when(taskInstanceQuery.taskInvolvedUser(anyString())).thenReturn(taskInstanceQuery);
        lenient().when(taskInstanceQuery.count()).thenReturn(1L);

        when(monitoringUtil.formatHistoricalEvents(anyList())).thenReturn(Collections.emptyList());

        // Act
        ProcessHistoryResponse result = processMonitoringService.getProcessInstanceHistory(
                PROCESS_INSTANCE_ID, 0, 50, userContext);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getEvents().size());
        assertEquals(0, result.getPage());
        assertEquals(50, result.getSize());
    }

    @Test
    void getProcessByBusinessKey_shouldReturnProcess_whenExists() {
        // Arrange
        mockProcessInstance = createMockProcessInstance("john.doe");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceBusinessKey(BUSINESS_KEY)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(mockProcessInstance);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(Collections.emptyList());
        when(taskQuery.count()).thenReturn(0L);

        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.finished()).thenReturn(taskInstanceQuery);
        when(taskInstanceQuery.count()).thenReturn(0L);

        when(monitoringUtil.getCurrentTaskInfo(anyList())).thenReturn(Collections.emptyMap());

        ProcessDefinition mockDefinition = mock(ProcessDefinition.class);
        when(mockDefinition.getName()).thenReturn("Purchase Requisition Approval");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(mockDefinition);

        // Act
        ProcessInstanceDTO result = processMonitoringService.getProcessByBusinessKey(BUSINESS_KEY, userContext);

        // Assert
        assertNotNull(result);
        assertEquals(PROCESS_INSTANCE_ID, result.getId());
        assertEquals(BUSINESS_KEY, result.getBusinessKey());
        verify(processInstanceQuery).processInstanceBusinessKey(BUSINESS_KEY);
    }

    @Test
    void getProcessByBusinessKey_shouldThrowException_whenNotFound() {
        // Arrange
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceBusinessKey(BUSINESS_KEY)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);

        // Act & Assert
        assertThrows(ProcessNotFoundException.class, () ->
                processMonitoringService.getProcessByBusinessKey(BUSINESS_KEY, userContext));
    }
}
