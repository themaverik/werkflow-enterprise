package com.werkflow.engine.service;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.UserProfileDto;
import com.werkflow.engine.dto.*;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.exception.UnauthorizedTaskAccessException;
import com.werkflow.engine.util.ProcessMonitoringUtil;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for process monitoring and tracking
 * Provides visibility into process instances, tasks, and historical events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessMonitoringService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final ProcessMonitoringUtil monitoringUtil;
    private final FlowableGroupResolver groupResolver;
    private final AdminServiceClient adminServiceClient;

    @Value("${werkflow.erp.enabled:false}")
    private boolean erpEnabled;

    /**
     * Get detailed information about a process instance
     * @param processInstanceId Process instance ID
     * @param includeVariables Whether to include process variables
     * @param userContext User context for authorization
     * @return Process instance details
     * @throws ProcessNotFoundException if process not found
     * @throws UnauthorizedTaskAccessException if user not authorized
     */
    public ProcessInstanceDTO getProcessInstanceDetails(String processInstanceId, boolean includeVariables,
                                                         JwtUserContext userContext) {
        log.info("Getting process instance details: {} for user: {}", processInstanceId, userContext.getUserId());

        // Get historic process instance (works for both active and completed)
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new ProcessNotFoundException("Process instance not found: " + processInstanceId);
        }

        // Check authorization - user must be initiator or involved in the process
        validateUserAccess(processInstanceId, historicInstance.getStartUserId(), userContext);

        // Build process instance DTO
        return buildProcessInstanceDTO(historicInstance, includeVariables);
    }

    /**
     * Get all tasks (active and completed) for a process instance
     * @param processInstanceId Process instance ID
     * @param includeHistory Whether to include completed tasks
     * @param status Filter by status (ACTIVE, COMPLETED, or null for all)
     * @param userContext User context for authorization
     * @return List of task history DTOs
     * @throws ProcessNotFoundException if process not found
     * @throws UnauthorizedTaskAccessException if user not authorized
     */
    public List<ProcessTaskHistoryDTO> getProcessInstanceTasks(String processInstanceId, boolean includeHistory,
                                                                 String status, JwtUserContext userContext) {
        log.info("Getting tasks for process instance: {} (includeHistory={}, status={}) for user: {}",
                processInstanceId, includeHistory, status, userContext.getUserId());

        // Verify process exists and user has access
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new ProcessNotFoundException("Process instance not found: " + processInstanceId);
        }

        validateUserAccess(processInstanceId, historicInstance.getStartUserId(), userContext);

        List<ProcessTaskHistoryDTO> tasks = new ArrayList<>();

        // Get active tasks if requested
        if (status == null || "ACTIVE".equalsIgnoreCase(status)) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list();

            tasks.addAll(activeTasks.stream()
                    .map(this::buildActiveTaskDTO)
                    .collect(Collectors.toList()));
        }

        // Get historic tasks if requested
        if (includeHistory && (status == null || "COMPLETED".equalsIgnoreCase(status))) {
            List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .finished()
                    .orderByHistoricTaskInstanceEndTime()
                    .asc()
                    .list();

            tasks.addAll(historicTasks.stream()
                    .map(this::buildHistoricTaskDTO)
                    .collect(Collectors.toList()));
        }

        log.info("Found {} tasks for process instance: {}", tasks.size(), processInstanceId);
        return tasks;
    }

    /**
     * Get historical events timeline for a process instance
     * @param processInstanceId Process instance ID
     * @param page Page number (zero-based)
     * @param size Page size
     * @param userContext User context for authorization
     * @return Process history response with events and pagination
     * @throws ProcessNotFoundException if process not found
     * @throws UnauthorizedTaskAccessException if user not authorized
     */
    public ProcessHistoryResponse getProcessInstanceHistory(String processInstanceId, int page, int size,
                                                              JwtUserContext userContext) {
        log.info("Getting history for process instance: {} (page={}, size={}) for user: {}",
                processInstanceId, page, size, userContext.getUserId());

        // Verify process exists and user has access
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new ProcessNotFoundException("Process instance not found: " + processInstanceId);
        }

        validateUserAccess(processInstanceId, historicInstance.getStartUserId(), userContext);

        // Get historic activities
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        // Format events
        List<ProcessEventHistoryDTO> allEvents = monitoringUtil.formatHistoricalEvents(activities);

        // Add task-specific events
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        allEvents.addAll(buildTaskEvents(historicTasks));

        // Sort all events by timestamp
        allEvents.sort(Comparator.comparing(ProcessEventHistoryDTO::getTimestamp));

        // Apply pagination
        int start = page * size;
        int end = Math.min(start + size, allEvents.size());
        List<ProcessEventHistoryDTO> paginatedEvents = start < allEvents.size() ?
                allEvents.subList(start, end) : Collections.emptyList();

        // Build response
        return ProcessHistoryResponse.builder()
                .events(paginatedEvents)
                .totalEvents((long) allEvents.size())
                .page(page)
                .size(size)
                .totalPages((int) Math.ceil((double) allEvents.size() / size))
                .build();
    }

    /**
     * Find process instance by business key
     * @param businessKey Business key (e.g., "PR-2025-00042", "CAPEX-2025-001")
     * @param userContext User context for authorization
     * @return Process instance details
     * @throws ProcessNotFoundException if process not found
     * @throws UnauthorizedTaskAccessException if user not authorized
     */
    public ProcessInstanceDTO getProcessByBusinessKey(String businessKey, JwtUserContext userContext) {
        log.info("Finding process by business key: {} for user: {}", businessKey, userContext.getUserId());

        // Query by business key
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult();

        if (historicInstance == null) {
            throw new ProcessNotFoundException("Process not found with business key: " + businessKey);
        }

        // Check authorization
        validateUserAccess(historicInstance.getId(), historicInstance.getStartUserId(), userContext);

        // Build and return DTO
        return buildProcessInstanceDTO(historicInstance, false);
    }

    /**
     * Build process instance DTO from historic instance
     * @param historicInstance Historic process instance
     * @param includeVariables Whether to include variables
     * @return Process instance DTO
     */
    private ProcessInstanceDTO buildProcessInstanceDTO(HistoricProcessInstance historicInstance,
                                                        boolean includeVariables) {
        ProcessInstanceDTO.ProcessInstanceDTOBuilder builder = ProcessInstanceDTO.builder()
                .id(historicInstance.getId())
                .businessKey(historicInstance.getBusinessKey())
                .processDefinitionKey(historicInstance.getProcessDefinitionKey())
                .name(getProcessDefinitionName(historicInstance.getProcessDefinitionId()))
                .status(determineProcessStatus(historicInstance))
                .startTime(historicInstance.getStartTime() != null ? historicInstance.getStartTime().toInstant() : null)
                .initiatorUsername(historicInstance.getStartUserId());

        // Set end time and duration if completed
        if (historicInstance.getEndTime() != null) {
            Instant endTime = historicInstance.getEndTime().toInstant();
            builder.endTime(endTime)
                    .durationInMinutes(monitoringUtil.calculateDuration(
                            historicInstance.getStartTime().toInstant(), endTime));
        }

        // Get current task info for active processes
        if (historicInstance.getEndTime() == null) {
            Map<String, String> currentTaskInfo = monitoringUtil.getCurrentTaskInfo(
                    taskService.createTaskQuery()
                            .processInstanceId(historicInstance.getId())
                            .list()
            );
            builder.currentTaskName(currentTaskInfo.get("taskName"))
                    .currentAssignee(currentTaskInfo.get("assignee"));
        }

        // Get task counts
        long activeTasks = taskService.createTaskQuery()
                .processInstanceId(historicInstance.getId())
                .count();
        long completedTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(historicInstance.getId())
                .finished()
                .count();

        builder.activeTaskCount((int) activeTasks)
                .completedTaskCount((int) completedTasks);

        // Include variables if requested
        if (includeVariables) {
            List<HistoricVariableInstance> variables = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .list();
            builder.variables(monitoringUtil.extractVariablesForDTO(variables));
        }

        return builder.build();
    }

    /**
     * Build task DTO from active task
     * @param task Active task
     * @return Task history DTO
     */
    private ProcessTaskHistoryDTO buildActiveTaskDTO(Task task) {
        return ProcessTaskHistoryDTO.builder()
                .taskId(task.getId())
                .name(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .status("ACTIVE")
                .assignedTo(task.getAssignee())
                .outcome("PENDING")
                .createdTime(task.getCreateTime().toInstant())
                .priority(task.getPriority())
                .description(task.getDescription())
                .build();
    }

    /**
     * Build task DTO from historic task
     * @param task Historic task instance
     * @return Task history DTO
     */
    private ProcessTaskHistoryDTO buildHistoricTaskDTO(HistoricTaskInstance task) {
        Instant createdTime = task.getCreateTime().toInstant();
        Instant completedTime = task.getEndTime() != null ? task.getEndTime().toInstant() : null;

        return ProcessTaskHistoryDTO.builder()
                .taskId(task.getId())
                .name(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .status("COMPLETED")
                .assignedTo(task.getAssignee())
                .completedBy(task.getAssignee())
                .outcome(monitoringUtil.determineTaskOutcome(task))
                .createdTime(createdTime)
                .completedTime(completedTime)
                .durationInMinutes(monitoringUtil.calculateDuration(createdTime, completedTime))
                .priority(task.getPriority())
                .description(task.getDescription())
                .build();
    }

    /**
     * Build event DTOs from historic tasks
     * @param tasks Historic task instances
     * @return List of event DTOs
     */
    private List<ProcessEventHistoryDTO> buildTaskEvents(List<HistoricTaskInstance> tasks) {
        List<ProcessEventHistoryDTO> events = new ArrayList<>();

        for (HistoricTaskInstance task : tasks) {
            // Task assigned event
            if (task.getCreateTime() != null) {
                events.add(ProcessEventHistoryDTO.builder()
                        .eventType("TASK_ASSIGNED")
                        .timestamp(task.getCreateTime().toInstant())
                        .userId(task.getAssignee())
                        .taskName(task.getName())
                        .details("Task '" + task.getName() + "' assigned to " + task.getAssignee())
                        .build());
            }

            // Task completed event
            if (task.getEndTime() != null) {
                String outcome = monitoringUtil.determineTaskOutcome(task);
                events.add(ProcessEventHistoryDTO.builder()
                        .eventType("TASK_COMPLETED")
                        .timestamp(task.getEndTime().toInstant())
                        .userId(task.getAssignee())
                        .taskName(task.getName())
                        .details("Task '" + task.getName() + "' completed with outcome: " + outcome)
                        .build());
            }
        }

        return events;
    }

    /**
     * Determine process status from historic instance
     * @param historicInstance Historic process instance
     * @return Status string
     */
    private String determineProcessStatus(HistoricProcessInstance historicInstance) {
        if (historicInstance.getEndTime() != null) {
            String deleteReason = historicInstance.getDeleteReason();
            if (deleteReason != null && deleteReason.toLowerCase().contains("terminated")) {
                return "TERMINATED";
            }
            return "COMPLETED";
        }

        // Check if process is suspended
        try {
            org.flowable.engine.runtime.ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .singleResult();

            if (runtimeInstance != null && runtimeInstance.isSuspended()) {
                return "SUSPENDED";
            }
        } catch (Exception e) {
            log.warn("Could not check if process is suspended: {}", e.getMessage());
        }

        return "RUNNING";
    }

    /**
     * Get process definition name from cache or repository
     * @param processDefinitionId Process definition ID
     * @return Process definition name
     */
    @Cacheable(value = "processDefinitionNames", key = "#processDefinitionId")
    public String getProcessDefinitionName(String processDefinitionId) {
        try {
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            return definition != null ? definition.getName() : "Unknown Process";
        } catch (Exception e) {
            log.warn("Could not get process definition name for: {}", processDefinitionId, e);
            return "Unknown Process";
        }
    }

    private String resolveUserDepartment(JwtUserContext userContext) {
        try {
            String tenantCode = userContext.getTenantCode() != null ? userContext.getTenantCode() : "default";
            UserProfileDto profile = adminServiceClient.getUserProfile(userContext.getUserId(), tenantCode);
            return profile != null ? profile.getDepartmentCode() : null;
        } catch (Exception e) {
            log.warn("ProcessMonitoringService: could not resolve ERP dept for {} — {}", userContext.getUserId(), e.getMessage());
            return null;
        }
    }

    /**
     * Validate user access to process instance
     * User must be the initiator or involved in any task
     * @param processInstanceId Process instance ID
     * @param startUserId User who started the process
     * @param userContext Current user context
     * @throws UnauthorizedTaskAccessException if user not authorized
     */
    private void validateUserAccess(String processInstanceId, String startUserId, JwtUserContext userContext) {
        // Admins and super admins can access any process
        boolean isAdmin = userContext.getRoles() != null && userContext.getRoles().stream()
            .anyMatch(r -> r.equalsIgnoreCase("SUPER_ADMIN") || r.equalsIgnoreCase("ADMIN"));
        if (isAdmin) {
            return;
        }

        String userId = userContext.getUserId();

        // Check if user is the initiator
        if (userId.equals(startUserId)) {
            return;
        }

        // Check if user is involved in any task
        long involvedTaskCount = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskInvolvedUser(userId)
                .count();

        if (involvedTaskCount > 0) {
            return;
        }

        // Check if user has any active tasks
        long activeTaskCount = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(userId)
                .count();

        if (activeTaskCount > 0) {
            return;
        }

        // Check if user is in candidate groups for any task
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        if (!userGroups.isEmpty()) {
            long candidateTaskCount = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .taskCandidateGroupIn(userGroups)
                    .count();

            if (candidateTaskCount > 0) {
                return;
            }
        }

        // Dept scoping — ADR-005: if ERP is enabled, allow access when owningDepartment matches user's dept
        if (erpEnabled) {
            String userDept = resolveUserDepartment(userContext);
            if (userDept != null) {
                List<HistoricVariableInstance> vars = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("owningDepartment")
                        .list();
                boolean deptMatch = vars.stream()
                        .anyMatch(v -> userDept.equals(String.valueOf(v.getValue())));
                if (deptMatch) {
                    return;
                }
            }
        }

        // User is not authorized
        log.warn("User {} attempted to access process {} without authorization", userId, processInstanceId);
        throw new UnauthorizedTaskAccessException(
                "You do not have permission to view this process instance");
    }
}
