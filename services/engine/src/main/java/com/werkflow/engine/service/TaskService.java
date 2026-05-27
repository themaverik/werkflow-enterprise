package com.werkflow.engine.service;

import com.werkflow.engine.dto.CompleteTaskRequest;
import com.werkflow.engine.dto.TaskResponse;
import com.werkflow.engine.exception.TaskNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing user tasks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final org.flowable.engine.TaskService flowableTaskService;
    private final RepositoryService repositoryService;

    /**
     * Get all tasks for a specific user
     */
    public List<TaskResponse> getTasksForUser(String userId) {
        log.debug("Fetching tasks for user: {}", userId);

        List<Task> tasks = flowableTaskService.createTaskQuery()
            .taskAssignee(userId)
            .active()
            .list();

        return tasks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get tasks for candidate groups
     */
    public List<TaskResponse> getTasksForCandidateGroups(List<String> groups) {
        log.debug("Fetching tasks for candidate groups: {}", groups);

        List<Task> tasks = flowableTaskService.createTaskQuery()
            .taskCandidateGroupIn(groups)
            .active()
            .list();

        return tasks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get unassigned tasks
     */
    public List<TaskResponse> getUnassignedTasks() {
        log.debug("Fetching unassigned tasks");

        List<Task> tasks = flowableTaskService.createTaskQuery()
            .taskUnassigned()
            .active()
            .list();

        return tasks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get tasks assigned to user OR available via candidate groups
     */
    public List<TaskResponse> getTasksForUserOrGroups(String userId, List<String> groups) {
        log.debug("Fetching tasks for user: {} or groups: {}", userId, groups);

        // Get assigned tasks
        List<Task> assignedTasks = flowableTaskService.createTaskQuery()
            .taskAssignee(userId)
            .active()
            .list();

        // Get candidate group tasks (unassigned)
        List<Task> candidateTasks = new java.util.ArrayList<>();
        if (groups != null && !groups.isEmpty()) {
            candidateTasks = flowableTaskService.createTaskQuery()
                .taskCandidateGroupIn(groups)
                .taskUnassigned()
                .active()
                .list();
        }

        // Merge and deduplicate by task ID
        Map<String, Task> taskMap = new java.util.LinkedHashMap<>();
        for (Task t : assignedTasks) taskMap.put(t.getId(), t);
        for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);

        return taskMap.values().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get tasks assigned to a group/role
     */
    public List<TaskResponse> getTasksForGroup(String groupId) {
        log.debug("Fetching tasks for group: {}", groupId);

        List<Task> tasks = flowableTaskService.createTaskQuery()
            .taskCandidateGroup(groupId)
            .active()
            .list();

        return tasks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Find an active task by ID (returns null if not found)
     */
    public Task findActiveTask(String taskId) {
        return flowableTaskService.createTaskQuery()
            .taskId(taskId)
            .singleResult();
    }

    /**
     * Get task by ID.
     *
     * <p>This is the only endpoint that populates {@link TaskResponse#getCanEscalate()}.
     * List endpoints use {@link #mapToResponse(Task)} directly and leave it null to avoid
     * N process-definition model lookups across potentially large task lists.
     */
    public TaskResponse getTaskById(String taskId) {
        log.debug("Fetching task by ID: {}", taskId);

        Task task = flowableTaskService.createTaskQuery()
            .taskId(taskId)
            .singleResult();

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        TaskResponse response = mapToResponse(task);
        response.setCanEscalate(computeCanEscalate(task));
        return response;
    }

    /**
     * Determines whether the BPMN gateway immediately following this task routes
     * {@code decision='escalate'} to a next-level approver.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Resolve the {@link UserTask} flow element by the task's definition key.</li>
     *   <li>Follow its single outgoing {@link SequenceFlow} to the target element.</li>
     *   <li>If the target is an {@link ExclusiveGateway}, inspect its outgoing flows.</li>
     *   <li>Return {@code true} iff at least one outgoing condition contains the token
     *       {@code "escalate"}.</li>
     * </ol>
     *
     * <p>Fail-closed: returns {@code false} on any exception, null process definition, or
     * ambiguous topology. Never throws.
     *
     * @param task the active Flowable task to inspect
     * @return {@code true} if an escalate route exists; {@code false} otherwise
     */
    private boolean computeCanEscalate(Task task) {
        try {
            if (task.getProcessDefinitionId() == null || task.getTaskDefinitionKey() == null) {
                return false;
            }

            BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
            if (model == null) {
                return false;
            }

            FlowElement rawUserTask = model.getFlowElement(task.getTaskDefinitionKey());
            if (!(rawUserTask instanceof UserTask userTask)) {
                return false;
            }

            List<SequenceFlow> outgoing = userTask.getOutgoingFlows();
            if (outgoing == null || outgoing.size() != 1) {
                // Unexpected topology — fail-closed.
                return false;
            }

            FlowElement target = outgoing.get(0).getTargetFlowElement();
            if (!(target instanceof ExclusiveGateway gateway)) {
                return false;
            }

            List<SequenceFlow> gatewayFlows = gateway.getOutgoingFlows();
            if (gatewayFlows == null) {
                return false;
            }

            return gatewayFlows.stream()
                .map(SequenceFlow::getConditionExpression)
                .filter(expr -> expr != null && !expr.isBlank())
                .anyMatch(expr -> expr.contains("escalate"));

        } catch (Exception ex) {
            log.warn("computeCanEscalate: could not inspect BPMN model for task {} (def={}): {}",
                task.getId(), task.getProcessDefinitionId(), ex.getMessage());
            return false;
        }
    }

    /**
     * Get tasks for a process instance
     */
    public List<TaskResponse> getTasksByProcessInstanceId(String processInstanceId) {
        log.debug("Fetching tasks for process instance: {}", processInstanceId);

        List<Task> tasks = flowableTaskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .active()
            .list();

        return tasks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Claim a task for a user
     */
    @Transactional
    public void claimTask(String taskId, String userId) {
        log.info("User {} claiming task: {}", userId, taskId);

        flowableTaskService.claim(taskId, userId);

        log.info("Task claimed successfully");
    }

    /**
     * Unclaim a task (return to candidate pool)
     */
    @Transactional
    public void unclaimTask(String taskId) {
        log.info("Unclaiming task: {}", taskId);

        flowableTaskService.unclaim(taskId);

        log.info("Task unclaimed successfully");
    }

    /**
     * Assign a task to a user
     */
    @Transactional
    public void assignTask(String taskId, String userId) {
        log.info("Assigning task {} to user: {}", taskId, userId);

        flowableTaskService.setAssignee(taskId, userId);

        log.info("Task assigned successfully");
    }

    /**
     * Complete a task
     */
    @Transactional
    public void completeTask(String taskId, CompleteTaskRequest request, String userId) {
        log.info("User {} completing task: {}", userId, taskId);

        Map<String, Object> variables = request.getVariables() != null ?
            request.getVariables() : new HashMap<>();

        flowableTaskService.complete(taskId, variables);

        log.info("Task completed successfully");
    }

    /**
     * Get task variables
     */
    public Map<String, Object> getTaskVariables(String taskId) {
        log.debug("Fetching variables for task: {}", taskId);

        return flowableTaskService.getVariables(taskId);
    }

    /**
     * Set task variables
     */
    @Transactional
    public void setTaskVariables(String taskId, Map<String, Object> variables) {
        log.info("Setting variables for task: {}", taskId);

        flowableTaskService.setVariables(taskId, variables);

        log.info("Variables set successfully");
    }

    /**
     * Add a comment to a task
     */
    @Transactional
    public void addComment(String taskId, String processInstanceId, String message) {
        log.info("Adding comment to task: {}", taskId);

        flowableTaskService.addComment(taskId, processInstanceId, message);

        log.info("Comment added successfully");
    }

    /**
     * Map Task entity to response DTO.
     * Resolves processDefinitionKey and processDefinitionName from the repository so
     * the portal task cards can display a human-readable process name instead of raw IDs.
     */
    private TaskResponse mapToResponse(Task task) {
        Map<String, Object> variables = flowableTaskService.getVariables(task.getId());

        List<IdentityLink> identityLinks = flowableTaskService.getIdentityLinksForTask(task.getId());
        List<String> candidateGroups = identityLinks.stream()
            .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getGroupId() != null)
            .map(IdentityLink::getGroupId)
            .collect(Collectors.toList());
        List<String> candidateUsers = identityLinks.stream()
            .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getUserId() != null)
            .map(IdentityLink::getUserId)
            .collect(Collectors.toList());

        String processDefinitionKey = null;
        String processDefinitionName = null;
        if (task.getProcessDefinitionId() != null) {
            try {
                ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(task.getProcessDefinitionId())
                    .singleResult();
                if (pd != null) {
                    processDefinitionKey = pd.getKey();
                    processDefinitionName = pd.getName();
                }
            } catch (Exception e) {
                log.warn("Could not resolve process definition for task {}: {}", task.getId(), e.getMessage());
            }
        }

        return TaskResponse.builder()
            .id(task.getId())
            .name(task.getName())
            .description(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(task.getProcessDefinitionId())
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .assignee(task.getAssignee())
            .owner(task.getOwner())
            .priority(task.getPriority())
            .createTime(task.getCreateTime() != null ? task.getCreateTime().toInstant() : null)
            .dueDate(task.getDueDate() != null ? task.getDueDate().toInstant() : null)
            .claimTime(task.getClaimTime() != null ? task.getClaimTime().toInstant() : null)
            .suspended(task.isSuspended())
            .formKey(task.getFormKey())
            .category(task.getCategory())
            .tenantId(task.getTenantId())
            .variables(variables)
            .candidateGroups(candidateGroups)
            .candidateUsers(candidateUsers)
            .build();
    }
}
