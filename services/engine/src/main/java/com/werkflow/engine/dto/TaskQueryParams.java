package com.werkflow.engine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Query parameters for task list endpoints
 * Supports pagination, filtering, and sorting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueryParams {

    @Min(0)
    @Builder.Default
    private Integer page = 0;

    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer size = 20;

    @Builder.Default
    private String sort = "createTime,desc";

    private TaskStatus status;

    @Size(max = 255)
    private String search;

    @Min(0)
    @Max(100)
    private Integer priority;

    private String processDefinitionKey;

    private String groupId;

    @Builder.Default
    private Boolean includeAssigned = false;

    private Instant dueBefore;

    private Instant dueAfter;

    /**
     * Task status enum for filtering
     */
    public enum TaskStatus {
        ACTIVE, SUSPENDED
    }
}
