package com.werkflow.engine.util;

import com.werkflow.engine.dto.ProcessEventHistoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for process monitoring operations
 * Provides helper methods for calculating durations, formatting events, and extracting data
 */
@Component
@Slf4j
public class ProcessMonitoringUtil {

    // Sensitive variable names that should not be exposed in responses
    private static final Set<String> SENSITIVE_VARIABLES = Set.of(
            "password", "token", "secret", "api_key", "apiKey",
            "credentials", "authorization", "auth_token", "authToken"
    );

    // Internal infrastructure variables that should not be exposed in responses
    private static final Set<String> INTERNAL_VARIABLES = Set.of(
            "serviceurl", "service_url"
    );

    /**
     * Calculate duration between two timestamps in minutes
     * @param startTime Start timestamp
     * @param endTime End timestamp (can be null)
     * @return Duration in minutes, or null if endTime is null
     */
    public Long calculateDuration(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Duration.between(startTime, endTime).toMinutes();
    }

    /**
     * Extract variables for DTO, filtering out sensitive data
     * @param variables List of historic variable instances
     * @return Map of variable names to values (without sensitive data)
     */
    public Map<String, Object> extractVariablesForDTO(List<HistoricVariableInstance> variables) {
        if (variables == null || variables.isEmpty()) {
            return Collections.emptyMap();
        }

        return variables.stream()
                .filter(var -> !isSensitiveVariable(var.getVariableName()))
                .filter(var -> var.getValue() != null)
                .collect(Collectors.toMap(
                        HistoricVariableInstance::getVariableName,
                        HistoricVariableInstance::getValue,
                        (v1, v2) -> v2, // In case of duplicates, take the latest
                        LinkedHashMap::new
                ));
    }

    /**
     * Check if a variable name is sensitive
     * @param variableName Variable name
     * @return true if the variable should be filtered out
     */
    private boolean isSensitiveVariable(String variableName) {
        if (variableName == null) {
            return true;
        }
        String lowerName = variableName.toLowerCase();
        return SENSITIVE_VARIABLES.stream().anyMatch(lowerName::contains)
                || INTERNAL_VARIABLES.stream().anyMatch(lowerName::contains);
    }

    /**
     * Format historical activities into timeline events
     * @param activities List of historic activity instances
     * @return List of formatted event DTOs
     */
    public List<ProcessEventHistoryDTO> formatHistoricalEvents(List<HistoricActivityInstance> activities) {
        if (activities == null || activities.isEmpty()) {
            return Collections.emptyList();
        }

        return activities.stream()
                .filter(activity -> isRelevantActivity(activity.getActivityType()))
                .map(this::buildEventFromActivity)
                .sorted(Comparator.comparing(ProcessEventHistoryDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Check if an activity type should be included in the timeline
     * @param activityType Activity type
     * @return true if the activity is relevant for timeline
     */
    private boolean isRelevantActivity(String activityType) {
        return activityType != null && (
                activityType.equals("startEvent") ||
                activityType.equals("endEvent") ||
                activityType.equals("userTask") ||
                activityType.equals("serviceTask") ||
                activityType.equals("exclusiveGateway")
        );
    }

    /**
     * Build an event DTO from an activity instance
     * @param activity Historic activity instance
     * @return Event DTO
     */
    private ProcessEventHistoryDTO buildEventFromActivity(HistoricActivityInstance activity) {
        String eventType = mapActivityTypeToEventType(activity.getActivityType());
        String details = buildActivityDetails(activity);

        return ProcessEventHistoryDTO.builder()
                .eventType(eventType)
                .timestamp(activity.getStartTime() != null ?
                        activity.getStartTime().toInstant() : Instant.now())
                .userId(activity.getAssignee())
                .taskName(activity.getActivityName())
                .details(details)
                .build();
    }

    /**
     * Map Flowable activity type to event type
     * @param activityType Flowable activity type
     * @return Event type for timeline
     */
    private String mapActivityTypeToEventType(String activityType) {
        return switch (activityType) {
            case "startEvent" -> "PROCESS_STARTED";
            case "endEvent" -> "PROCESS_ENDED";
            case "userTask" -> "TASK_COMPLETED";
            case "serviceTask" -> "SERVICE_TASK_EXECUTED";
            case "exclusiveGateway" -> "GATEWAY_EVALUATED";
            default -> "ACTIVITY_COMPLETED";
        };
    }

    /**
     * Build human-readable details for an activity
     * @param activity Historic activity instance
     * @return Details string
     */
    private String buildActivityDetails(HistoricActivityInstance activity) {
        String activityType = activity.getActivityType();
        String activityName = activity.getActivityName();

        return switch (activityType) {
            case "startEvent" -> "Process started";
            case "endEvent" -> "Process completed";
            case "userTask" -> "Task '" + activityName + "' completed";
            case "serviceTask" -> "Service task '" + activityName + "' executed";
            case "exclusiveGateway" -> "Gateway evaluated";
            default -> "Activity '" + activityName + "' completed";
        };
    }

    /**
     * Get current task information from active tasks
     * @param tasks List of active tasks
     * @return Map with current task name and assignee
     */
    public Map<String, String> getCurrentTaskInfo(List<org.flowable.task.api.Task> tasks) {
        Map<String, String> info = new HashMap<>();

        if (tasks == null || tasks.isEmpty()) {
            return info;
        }

        // Get the first active task (sorted by creation time)
        org.flowable.task.api.Task currentTask = tasks.stream()
                .min(Comparator.comparing(org.flowable.task.api.Task::getCreateTime))
                .orElse(null);

        if (currentTask != null) {
            info.put("taskName", currentTask.getName());
            info.put("assignee", currentTask.getAssignee());
        }

        return info;
    }

    /**
     * Determine task outcome from historic task instance
     * @param task Historic task instance
     * @return Outcome (APPROVED, REJECTED, PENDING, REASSIGNED)
     */
    public String determineTaskOutcome(HistoricTaskInstance task) {
        if (task.getEndTime() == null) {
            return "PENDING";
        }

        // Check if task was reassigned
        if (task.getAssignee() == null && task.getOwner() != null) {
            return "REASSIGNED";
        }

        // Try to determine outcome from delete reason or variables
        String deleteReason = task.getDeleteReason();
        if (deleteReason != null) {
            if (deleteReason.toLowerCase().contains("approved") ||
                deleteReason.toLowerCase().contains("approve")) {
                return "APPROVED";
            } else if (deleteReason.toLowerCase().contains("rejected") ||
                       deleteReason.toLowerCase().contains("reject")) {
                return "REJECTED";
            }
        }

        // Default to APPROVED if task was completed normally
        if ("completed".equalsIgnoreCase(deleteReason)) {
            return "APPROVED";
        }

        return "PENDING";
    }
}
