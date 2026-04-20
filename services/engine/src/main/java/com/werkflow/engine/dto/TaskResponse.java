package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for Task
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private String id;
    private String name;
    private String description;
    private String processInstanceId;
    private String processDefinitionId;
    private String taskDefinitionKey;
    private String assignee;
    private String owner;
    private Integer priority;
    private Instant createTime;
    private Instant dueDate;
    private Instant claimTime;
    private boolean suspended;
    private String formKey;
    private String category;
    private String tenantId;
    private Map<String, Object> variables;

    // Enhanced fields for task list endpoints
    private String processDefinitionKey;
    private String processDefinitionName;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private Long executionDuration; // milliseconds since creation
    private String department; // department associated with task
}
