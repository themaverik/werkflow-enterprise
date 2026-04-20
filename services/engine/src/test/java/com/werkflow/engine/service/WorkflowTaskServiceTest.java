package com.werkflow.engine.service;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.TaskListResponse;
import com.werkflow.engine.dto.TaskQueryParams;
import com.werkflow.engine.exception.UnauthorizedTaskAccessException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowTaskService
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTaskServiceTest {

    @Mock
    private org.flowable.engine.TaskService flowableTaskService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private ProcessDefinitionQuery processDefinitionQuery;

    @Mock
    private ProcessDefinition processDefinition;

    @InjectMocks
    private WorkflowTaskService workflowTaskService;

    private JwtUserContext userContext;
    private TaskQueryParams defaultParams;

    @BeforeEach
    void setUp() {
        userContext = JwtUserContext.builder()
                .userId("john.doe")
                .email("john.doe@example.com")
                .fullName("John Doe")
                .department("HR")
                .groups(List.of("HR_STAFF", "HR_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(2)
                .build();

        defaultParams = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("createTime,desc")
                .build();
    }

    @Test
    void getMyTasks_shouldReturnUserAssignedTasks() {
        // Arrange
        List<Task> mockTasks = createMockTasks(5);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(5L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        // Mock identity links and variables for each task
        setupTaskMocks(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, defaultParams);

        // Assert
        assertNotNull(response);
        assertEquals(5, response.getContent().size());
        assertEquals(0, response.getPage().getNumber());
        assertEquals(20, response.getPage().getSize());
        assertEquals(5, response.getPage().getTotalElements());
        assertEquals(1, response.getPage().getTotalPages());

        verify(taskQuery).taskAssignee("john.doe");
        verify(taskQuery).active();
        verify(taskQuery).count();
        verify(taskQuery).listPage(0, 20);
    }

    @Test
    void getMyTasks_shouldApplySearchFilter() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("createTime,desc")
                .search("leave")
                .build();

        List<Task> mockTasks = createMockTasks(2);
        setupTaskMocks(mockTasks);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.or()).thenReturn(taskQuery);
        when(taskQuery.taskNameLikeIgnoreCase(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDescriptionLikeIgnoreCase(anyString())).thenReturn(taskQuery);
        when(taskQuery.endOr()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(2L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        verify(taskQuery).taskNameLikeIgnoreCase("%leave%");
        verify(taskQuery).taskDescriptionLikeIgnoreCase("%leave%");
    }

    @Test
    void getMyTasks_shouldApplyPriorityFilter() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("priority,desc")
                .priority(50)
                .build();

        List<Task> mockTasks = createMockTasks(1);
        setupTaskMocks(mockTasks);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.taskPriority(anyInt())).thenReturn(taskQuery);
        when(taskQuery.orderByTaskPriority()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        // Assert
        assertNotNull(response);
        verify(taskQuery).taskPriority(50);
        verify(taskQuery).orderByTaskPriority();
    }

    @Test
    void getGroupTasks_shouldReturnCandidateTasksForUserGroups() {
        // Arrange
        List<Task> mockTasks = createMockTasks(3);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.taskUnassigned()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(3L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        setupTaskMocks(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getGroupTasks(userContext, defaultParams);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getContent().size());
        verify(taskQuery).taskCandidateGroupIn(List.of("HR_STAFF", "HR_MANAGER"));
        verify(taskQuery).taskUnassigned();
    }

    @Test
    void getGroupTasks_shouldReturnEmptyWhenUserHasNoGroups() {
        // Arrange
        JwtUserContext userWithoutGroups = JwtUserContext.builder()
                .userId("jane.doe")
                .email("jane.doe@example.com")
                .groups(Collections.emptyList())
                .build();

        // Act
        TaskListResponse response = workflowTaskService.getGroupTasks(userWithoutGroups, defaultParams);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getContent().size());
        assertEquals(0, response.getPage().getTotalElements());
        verify(flowableTaskService, never()).createTaskQuery();
    }

    @Test
    void getGroupTasks_shouldFilterBySpecificGroup() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("createTime,desc")
                .groupId("HR_STAFF")
                .build();

        List<Task> mockTasks = createMockTasks(2);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskCandidateGroup(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.taskUnassigned()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(2L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        setupTaskMocks(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getGroupTasks(userContext, params);

        // Assert
        assertNotNull(response);
        verify(taskQuery).taskCandidateGroup("HR_STAFF");
    }

    @Test
    void getGroupTasks_shouldThrowExceptionWhenUserNotInGroup() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("createTime,desc")
                .groupId("FINANCE_STAFF")  // User not in this group
                .build();

        // Act & Assert
        assertThrows(UnauthorizedTaskAccessException.class, () ->
                workflowTaskService.getGroupTasks(userContext, params)
        );
    }

    @Test
    void getGroupTasks_shouldIncludeAssignedTasksWhenRequested() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(0)
                .size(20)
                .sort("createTime,desc")
                .includeAssigned(true)
                .build();

        List<Task> mockTasks = createMockTasks(3);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(3L);
        when(taskQuery.listPage(0, 20)).thenReturn(mockTasks);

        setupTaskMocks(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getGroupTasks(userContext, params);

        // Assert
        assertNotNull(response);
        verify(taskQuery, never()).taskUnassigned();  // Should not filter out assigned tasks
    }

    @Test
    void getMyTasks_shouldHandlePagination() {
        // Arrange
        TaskQueryParams params = TaskQueryParams.builder()
                .page(1)  // Second page
                .size(10)
                .sort("createTime,desc")
                .build();

        List<Task> mockTasks = createMockTasks(10);

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(25L);  // Total 25 tasks
        when(taskQuery.listPage(10, 10)).thenReturn(mockTasks);

        setupTaskMocks(mockTasks);

        // Act
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPage().getNumber());
        assertEquals(10, response.getPage().getSize());
        assertEquals(25, response.getPage().getTotalElements());
        assertEquals(3, response.getPage().getTotalPages());
        verify(taskQuery).listPage(10, 10);  // Offset = page * size = 1 * 10 = 10
    }

    // Helper methods

    private List<Task> createMockTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-" + i);
            when(task.getName()).thenReturn("Task " + i);
            when(task.getDescription()).thenReturn("Description " + i);
            when(task.getProcessInstanceId()).thenReturn("proc-inst-" + i);
            when(task.getProcessDefinitionId()).thenReturn("leave-request:1:def-id-" + i);
            when(task.getTaskDefinitionKey()).thenReturn("reviewTask");
            when(task.getAssignee()).thenReturn("john.doe");
            when(task.getPriority()).thenReturn(50);
            when(task.getCreateTime()).thenReturn(new Date());
            when(task.isSuspended()).thenReturn(false);
            tasks.add(task);
        }
        return tasks;
    }

    private void setupTaskMocks(List<Task> tasks) {
        for (Task task : tasks) {
            // Mock identity links
            IdentityLink groupLink = mock(IdentityLink.class);
            when(groupLink.getType()).thenReturn("candidate");
            when(groupLink.getGroupId()).thenReturn("HR_STAFF");
            when(flowableTaskService.getIdentityLinksForTask(task.getId()))
                    .thenReturn(List.of(groupLink));

            // Mock variables
            when(flowableTaskService.getVariables(task.getId()))
                    .thenReturn(Map.of("department", "HR", "employeeName", "John Doe"));
        }

        // Mock process definition query
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        when(processDefinition.getName()).thenReturn("Leave Request Process");
    }
}
