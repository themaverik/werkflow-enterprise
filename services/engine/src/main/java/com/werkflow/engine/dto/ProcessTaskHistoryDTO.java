package com.werkflow.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for task history in a process instance
 * Represents both active and completed tasks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Task history details for a process instance")
public class ProcessTaskHistoryDTO {

    @Schema(description = "Task ID", example = "67890")
    private String taskId;

    @Schema(description = "Task name", example = "Manager Approval")
    private String name;

    @Schema(description = "Task definition key", example = "managerApproval")
    private String taskDefinitionKey;

    @Schema(description = "Task status", example = "COMPLETED", allowableValues = {"ACTIVE", "COMPLETED"})
    private String status;

    @Schema(description = "Username of the user to whom this task was assigned", example = "jane.smith")
    private String assignedTo;

    @Schema(description = "Username of the user who completed this task", example = "jane.smith")
    private String completedBy;

    @Schema(description = "Task outcome", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED", "PENDING", "REASSIGNED"})
    private String outcome;

    @Schema(description = "Task creation time", example = "2025-11-29T10:35:00Z")
    private Instant createdTime;

    @Schema(description = "Task completion time (null if still active)", example = "2025-11-29T11:00:00Z")
    private Instant completedTime;

    @Schema(description = "Time spent on task in minutes (null if still active)", example = "25")
    private Long durationInMinutes;

    @Schema(description = "Task priority", example = "50")
    private Integer priority;

    @Schema(description = "Task description", example = "Review and approve purchase requisition")
    private String description;
}
