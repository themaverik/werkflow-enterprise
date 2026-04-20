package com.werkflow.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for historical events in a process instance
 * Represents timeline of activities in the workflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Historical event in process instance timeline")
public class ProcessEventHistoryDTO {

    @Schema(description = "Event type", example = "TASK_COMPLETED",
            allowableValues = {"PROCESS_STARTED", "TASK_ASSIGNED", "TASK_CLAIMED", "TASK_COMPLETED", "VARIABLE_CHANGED", "PROCESS_ENDED"})
    private String eventType;

    @Schema(description = "Event timestamp", example = "2025-11-29T10:35:00Z")
    private Instant timestamp;

    @Schema(description = "Username of the user who triggered this event", example = "john.doe")
    private String userId;

    @Schema(description = "User's full name", example = "John Doe")
    private String userFullName;

    @Schema(description = "Task name (for task-related events)", example = "Manager Approval")
    private String taskName;

    @Schema(description = "Event details (human-readable description)", example = "Task 'Manager Approval' completed with outcome APPROVED")
    private String details;

    @Schema(description = "Additional event data")
    private Map<String, Object> data;
}
