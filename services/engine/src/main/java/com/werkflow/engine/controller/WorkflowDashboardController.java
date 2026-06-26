package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.util.JwtClaimsExtractor;
import com.werkflow.engine.workflow.FlowableGroupResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for dashboard-level workflow queries.
 * Provides /workflows/instances, /workflows/activity, and /api/v1/tasks/summary
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkflowDashboardController {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final FlowableGroupResolver groupResolver;

    /**
     * GET /workflows/instances — list all workflow instances (active + completed)
     */
    @GetMapping("/workflows/instances")
    public ResponseEntity<List<Map<String, Object>>> getWorkflowInstances(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startedBy,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String tenantId) {

        String jwtTenantId = jwtClaimsExtractor.getTenantCode(jwt);
        String effectiveTenantId = (hasSuperAdminRole(jwt) && tenantId != null && !tenantId.isBlank())
                ? tenantId : jwtTenantId;
        limit = Math.min(limit, 500);
        log.info("GET /workflows/instances - status={}, startedBy={}, limit={}, tenant={}", status, startedBy, limit, effectiveTenantId);

        List<Map<String, Object>> results = new ArrayList<>();

        if (status == null || "active".equals(status)) {
            var query = runtimeService.createProcessInstanceQuery()
                    .processInstanceTenantId(effectiveTenantId)
                    .orderByProcessInstanceId().desc();
            if (startedBy != null && !startedBy.isEmpty()) {
                query.startedBy(startedBy);
            }
            List<ProcessInstance> active = query.listPage(0, limit);
            for (ProcessInstance pi : active) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", pi.getId());
                entry.put("processDefinitionKey", pi.getProcessDefinitionKey());
                entry.put("processDefinitionName", pi.getProcessDefinitionName());
                entry.put("businessKey", pi.getBusinessKey());
                entry.put("startTime", pi.getStartTime() != null ? pi.getStartTime().toInstant().toString() : null);
                entry.put("startedBy", pi.getStartUserId());
                entry.put("status", pi.isSuspended() ? "suspended" : "active");
                entry.put("department", runtimeService.getVariable(pi.getId(), "department"));

                // Resolve current active user task name for the instance
                List<org.flowable.task.api.Task> activeTasks = taskService.createTaskQuery()
                        .processInstanceId(pi.getId())
                        .active()
                        .list();
                String currentActivity = activeTasks.stream()
                        .map(t -> t.getName() != null ? t.getName() : t.getTaskDefinitionKey())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                entry.put("currentActivity", currentActivity);

                results.add(entry);
            }
        }

        if (status == null || "completed".equals(status) || "failed".equals(status)) {
            var hQuery = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceTenantId(effectiveTenantId)
                    .finished()
                    .orderByProcessInstanceEndTime().desc();
            if (startedBy != null && !startedBy.isEmpty()) {
                hQuery.startedBy(startedBy);
            }
            List<HistoricProcessInstance> finished = hQuery.listPage(0, limit);
            for (HistoricProcessInstance hpi : finished) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", hpi.getId());
                entry.put("processDefinitionKey", hpi.getProcessDefinitionKey());
                entry.put("processDefinitionName", hpi.getProcessDefinitionName());
                entry.put("businessKey", hpi.getBusinessKey());
                entry.put("startTime", hpi.getStartTime() != null ? hpi.getStartTime().toInstant().toString() : null);
                entry.put("endTime", hpi.getEndTime() != null ? hpi.getEndTime().toInstant().toString() : null);
                entry.put("startedBy", hpi.getStartUserId());
                String instanceStatus = hpi.getDeleteReason() != null ? "failed" : "completed";
                entry.put("status", instanceStatus);
                entry.put("currentActivity", "completed".equals(instanceStatus) ? "Completed" : "Rejected");
                results.add(entry);
            }
        }

        return ResponseEntity.ok(results);
    }

    /**
     * GET /workflows/activity — recent activity log from historic activity instances
     */
    @GetMapping("/workflows/activity")
    public ResponseEntity<List<Map<String, Object>>> getActivityLog(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String tenantId) {

        String jwtTenantId = jwtClaimsExtractor.getTenantCode(jwt);
        String effectiveTenantId = (hasSuperAdminRole(jwt) && tenantId != null && !tenantId.isBlank())
                ? tenantId : jwtTenantId;
        limit = Math.min(limit, 200);
        log.info("GET /workflows/activity - limit={}, tenant={}", limit, effectiveTenantId);

        // Fetch more than limit so we have enough after filtering noise
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .activityTenantId(effectiveTenantId)
                .orderByHistoricActivityInstanceEndTime().desc()
                .finished()
                .listPage(0, limit * 10);

        List<Map<String, Object>> results = activities.stream()
                .filter(a -> isMeaningfulActivity(a))
                .limit(limit)
                .map(a -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", a.getId());
                    entry.put("type", mapActivityType(a.getActivityType()));
                    entry.put("message", buildActivityMessage(a));
                    entry.put("timestamp", a.getEndTime() != null ? a.getEndTime().toInstant().toString() : null);
                    entry.put("user", a.getAssignee() != null ? a.getAssignee() : "system");
                    return entry;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/v1/tasks/summary — task counts for dashboard
     */
    @GetMapping("/api/v1/tasks/summary")
    public ResponseEntity<Map<String, Object>> getTaskSummary(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);
        String tenantId = userContext.getTenantCode();
        List<String> userGroups = groupResolver.resolveGroups(userContext);
        log.info("GET /api/v1/tasks/summary - user={}, tenant={}", userId, tenantId);

        long myTasks = taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee(userId).count();
        // Team tasks = tasks claimable by the user's candidate groups (matches
        // TaskService.getTasksForCandidateGroups). taskCandidateGroupIn throws on an empty list.
        long teamTasks = userGroups.isEmpty() ? 0L
                : taskService.createTaskQuery().taskTenantId(tenantId).taskCandidateGroupIn(userGroups).count();
        long unassigned = taskService.createTaskQuery().taskTenantId(tenantId).taskUnassigned().count();
        long overdue = taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee(userId)
                .taskDueBefore(new Date()).count();
        long highPriority = taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee(userId)
                .taskMinPriority(75).count();

        // dueToday: tasks due between now and end of day
        Calendar endOfDay = Calendar.getInstance();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        long dueToday = taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee(userId)
                .taskDueBefore(endOfDay.getTime()).taskDueAfter(new Date()).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", myTasks + teamTasks);
        summary.put("myTasks", myTasks);
        summary.put("teamTasks", teamTasks);
        summary.put("unassigned", unassigned);
        summary.put("overdue", overdue);
        summary.put("dueToday", dueToday);
        summary.put("highPriority", highPriority);

        return ResponseEntity.ok(summary);
    }

    /**
     * Returns true only for activity types that carry meaningful information for end users.
     * Filters out sequenceFlow, exclusiveGateway, and scriptTasks whose names look like
     * internal BPMN IDs (no spaces, starts with lowercase, or contains digits like flow7).
     */
    private boolean isMeaningfulActivity(HistoricActivityInstance a) {
        String type = a.getActivityType();
        if (type == null) return false;
        return switch (type) {
            case "sequenceFlow", "exclusiveGateway", "inclusiveGateway", "parallelGateway" -> false;
            case "scriptTask" -> {
                // Only show scriptTask if the name looks human-readable (has spaces or title-cased)
                String name = a.getActivityName();
                if (name == null || name.isBlank()) yield false;
                // Reject names that look like BPMN internal IDs: no spaces, camelCase or all-lower
                yield name.contains(" ") || Character.isUpperCase(name.charAt(0));
            }
            default -> true;
        };
    }

    private String mapActivityType(String flowableType) {
        if (flowableType == null) return "started";
        return switch (flowableType) {
            case "startEvent" -> "started";
            case "endEvent" -> "completed";
            case "userTask" -> "completed";
            case "serviceTask" -> "completed";
            case "boundaryEvent", "errorEndEvent" -> "failed";
            default -> "started";
        };
    }

    private String buildActivityMessage(HistoricActivityInstance a) {
        String name = a.getActivityName() != null ? a.getActivityName() : a.getActivityId();
        return switch (a.getActivityType()) {
            case "startEvent" -> "Process started";
            case "endEvent" -> "Process completed";
            case "userTask" -> "Task '" + name + "' completed";
            case "serviceTask" -> "Service task '" + name + "' executed";
            default -> "Activity '" + name + "' (" + a.getActivityType() + ")";
        };
    }

    private boolean hasSuperAdminRole(Jwt jwt) {
        java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof java.util.Collection<?> r) {
                return r.contains("SUPER_ADMIN") || r.contains("super_admin");
            }
        }
        return false;
    }
}
