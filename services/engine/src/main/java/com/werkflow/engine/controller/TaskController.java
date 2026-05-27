package com.werkflow.engine.controller;

import com.werkflow.engine.dto.CompleteTaskRequest;
import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.TaskResponse;
import com.werkflow.engine.service.TaskService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import org.flowable.engine.HistoryService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for managing user tasks
 */
@RestController
@RequestMapping({"/api/tasks", "/api/v1/tasks"})
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "User task management")
@SecurityRequirement(name = "bearer-jwt")
public class TaskController {

    private final TaskService taskService;
    private final HistoryService historyService;
    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final FlowableGroupResolver groupResolver;
    private final org.flowable.engine.TaskService flowableTaskService;

    @GetMapping
    @Operation(summary = "List tasks for current user (assigned + candidate)")
    public ResponseEntity<Map<String, Object>> listTasks(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int start,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createTime") String sort,
        @RequestParam(defaultValue = "desc") String order,
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) String candidateUser,
        @RequestParam(required = false) String candidateGroups,
        @RequestParam(required = false) Boolean unassigned,
        @RequestParam(required = false) Integer priority,
        @RequestParam(required = false) String dueBefore,
        @RequestParam(required = false) String dueAfter
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        List<TaskResponse> tasks;

        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);

        if (assignee != null && !assignee.isEmpty()) {
            // My Tasks: restrict to current user only (H5 — prevent arbitrary user query)
            tasks = taskService.getTasksForUser(userId);
        } else if (candidateGroups != null && !candidateGroups.isEmpty()) {
            // Team/Group Tasks: intersect requested groups with user's own groups (H5)
            Set<String> userGroupSet = new java.util.HashSet<>(userGroups);
            List<String> allowedGroups = List.of(candidateGroups.split(",")).stream()
                .filter(userGroupSet::contains)
                .collect(java.util.stream.Collectors.toList());
            tasks = allowedGroups.isEmpty()
                ? List.of()
                : taskService.getTasksForCandidateGroups(allowedGroups);
        } else if (unassigned != null && unassigned) {
            // Unassigned tasks
            tasks = taskService.getUnassignedTasks();
        } else {
            // Default: assigned to user + candidate tasks for user's resolved groups
            tasks = taskService.getTasksForUserOrGroups(userId, userGroups);
        }

        if (priority != null) {
            final int p = priority;
            tasks = tasks.stream()
                .filter(t -> t.getPriority() != null && t.getPriority() == p)
                .collect(java.util.stream.Collectors.toList());
        }
        if (dueBefore != null) {
            Instant before = Instant.parse(dueBefore);
            tasks = tasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(before))
                .collect(java.util.stream.Collectors.toList());
        }
        if (dueAfter != null) {
            Instant after = Instant.parse(dueAfter);
            tasks = tasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isAfter(after))
                .collect(java.util.stream.Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", tasks);
        result.put("total", tasks.size());
        result.put("start", start);
        result.put("size", size);
        result.put("sort", sort);
        result.put("order", order);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-tasks")
    @Operation(summary = "Get tasks assigned to current user")
    public ResponseEntity<List<TaskResponse>> getMyTasks(@AuthenticationPrincipal Jwt jwt) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        List<TaskResponse> responses = taskService.getTasksForUser(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get tasks for a group/role")
    public ResponseEntity<List<TaskResponse>> getTasksForGroup(
        @Parameter(description = "Group/Role ID") @PathVariable String groupId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // H6: only return tasks for groups the caller belongs to
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        if (!userGroups.contains(groupId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "User is not a member of group: " + groupId);
        }
        List<TaskResponse> responses = taskService.getTasksForGroup(groupId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    public ResponseEntity<TaskResponse> getTaskById(
        @Parameter(description = "Task ID") @PathVariable String id
    ) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/process-instance/{processInstanceId}")
    @Operation(summary = "Get tasks for a process instance")
    public ResponseEntity<List<TaskResponse>> getTasksByProcessInstanceId(
        @Parameter(description = "Process instance ID") @PathVariable String processInstanceId
    ) {
        List<TaskResponse> responses = taskService.getTasksByProcessInstanceId(processInstanceId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim a task")
    public ResponseEntity<Void> claimTask(
        @Parameter(description = "Task ID") @PathVariable String id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups); // C1: must be candidate group member
        taskService.claimTask(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unclaim")
    @Operation(summary = "Unclaim a task")
    public ResponseEntity<Void> unclaimTask(
        @Parameter(description = "Task ID") @PathVariable String id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups);
        taskService.unclaimTask(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign a task to a user (admin only)")
    @PreAuthorize("hasPermission(null, 'TASK:MANAGE')")
    public ResponseEntity<Void> assignTask(
        @Parameter(description = "Task ID") @PathVariable String id,
        @Parameter(description = "User ID to assign") @RequestParam String userId
    ) {
        taskService.assignTask(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a task")
    public ResponseEntity<Void> completeTask(
        @Parameter(description = "Task ID") @PathVariable String id,
        @RequestBody(required = false) CompleteTaskRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups); // C2: must be assignee or candidate group member
        CompleteTaskRequest finalRequest = request != null ? request : new CompleteTaskRequest();
        taskService.completeTask(id, finalRequest, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/variables")
    @Operation(summary = "Get task variables")
    public ResponseEntity<Map<String, Object>> getTaskVariables(
        @Parameter(description = "Task ID") @PathVariable String id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups); // C3: must be assignee or candidate group member
        Map<String, Object> variables = taskService.getTaskVariables(id);
        return ResponseEntity.ok(variables);
    }

    @PutMapping("/{id}/variables")
    @Operation(summary = "Set task variables")
    public ResponseEntity<Void> setTaskVariables(
        @Parameter(description = "Task ID") @PathVariable String id,
        @RequestBody Map<String, Object> variables,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups); // C3: must be assignee or candidate group member
        taskService.setTaskVariables(id, variables);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get task history (audit trail)")
    public ResponseEntity<List<Map<String, Object>>> getTaskHistory(
        @Parameter(description = "Task ID") @PathVariable String id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups);

        List<Map<String, Object>> history = new java.util.ArrayList<>();

        String processInstanceId = null;

        org.flowable.task.api.Task activeTask = taskService.findActiveTask(id);
        if (activeTask != null) {
            processInstanceId = activeTask.getProcessInstanceId();
        } else {
            // Check historic tasks
            HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                    .taskId(id)
                    .singleResult();
            if (historicTask != null) {
                processInstanceId = historicTask.getProcessInstanceId();
            }
        }

        if (processInstanceId != null) {
            List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .orderByHistoricTaskInstanceStartTime().asc()
                    .list();

            for (HistoricTaskInstance task : tasks) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", task.getId());
                entry.put("taskId", task.getId());
                entry.put("action", task.getEndTime() != null ? "completed" : "active");
                entry.put("userId", task.getAssignee());
                entry.put("taskName", task.getName());
                entry.put("timestamp", task.getEndTime() != null ?
                        task.getEndTime().toInstant().toString() :
                        task.getStartTime().toInstant().toString());
                entry.put("startTime", task.getStartTime() != null ?
                        task.getStartTime().toInstant().toString() : null);
                entry.put("endTime", task.getEndTime() != null ?
                        task.getEndTime().toInstant().toString() : null);
                history.add(entry);
            }
        }

        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment to a task")
    public ResponseEntity<Void> addComment(
        @Parameter(description = "Task ID") @PathVariable String id,
        @Parameter(description = "Process instance ID") @RequestParam String processInstanceId,
        @Parameter(description = "Comment message") @RequestBody String message,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // L-4: use sub (stable Keycloak UUID) instead of preferred_username for all task ops
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        verifyTaskAccess(id, userId, userGroups);
        taskService.addComment(id, processInstanceId, message);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verifies the caller is either the current assignee OR a member of the task's candidate groups.
     * Throws 403 if the check fails. Used by claim, complete, and variable endpoints (C1/C2/C3).
     */
    private void verifyTaskAccess(String taskId, String userId, List<String> userGroups) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user identity");
        }
        Task task = flowableTaskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId);
        }
        // Assignee can always interact with their own task
        if (userId.equals(task.getAssignee())) return;

        // Check candidate group membership
        List<IdentityLink> links = flowableTaskService.getIdentityLinksForTask(taskId);
        Set<String> taskGroups = links.stream()
            .filter(l -> IdentityLinkType.CANDIDATE.equals(l.getType()) && l.getGroupId() != null)
            .map(IdentityLink::getGroupId)
            .collect(java.util.stream.Collectors.toSet());

        boolean isCandidate = userGroups.stream().anyMatch(taskGroups::contains);
        if (!isCandidate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "User is not authorized to interact with task: " + taskId);
        }
    }
}
