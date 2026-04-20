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
 * DTO for process instance details
 * Contains full information about a running or completed process instance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Process instance details including status, initiator, and variables")
public class ProcessInstanceDTO {

    @Schema(description = "Process instance ID", example = "12345")
    private String id;

    @Schema(description = "Process definition name", example = "Purchase Requisition Approval")
    private String name;

    @Schema(description = "Process definition key", example = "purchase-requisition-approval")
    private String processDefinitionKey;

    @Schema(description = "Business key for this process", example = "PR-2025-00042")
    private String businessKey;

    @Schema(description = "Process status", example = "RUNNING", allowableValues = {"RUNNING", "COMPLETED", "SUSPENDED", "TERMINATED"})
    private String status;

    @Schema(description = "Process start time", example = "2025-11-29T10:30:00Z")
    private Instant startTime;

    @Schema(description = "Process end time (null if still running)", example = "2025-11-29T11:45:00Z")
    private Instant endTime;

    @Schema(description = "Duration in minutes (null if still running)", example = "75")
    private Long durationInMinutes;

    @Schema(description = "Username of the user who initiated the process", example = "john.doe")
    private String initiatorUsername;

    @Schema(description = "Email of the user who initiated the process", example = "john.doe@company.com")
    private String initiatorEmail;

    @Schema(description = "Full name of the initiator", example = "John Doe")
    private String initiatorFullName;

    @Schema(description = "Current active task name (null if completed)", example = "Manager Approval")
    private String currentTaskName;

    @Schema(description = "Current task assignee (null if unassigned or completed)", example = "jane.smith")
    private String currentAssignee;

    @Schema(description = "Process variables (included when includeVariables=true)")
    private Map<String, Object> variables;

    @Schema(description = "Number of active tasks", example = "1")
    private Integer activeTaskCount;

    @Schema(description = "Number of completed tasks", example = "3")
    private Integer completedTaskCount;
}
