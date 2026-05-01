package com.werkflow.engine.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class TaskMetricsDTO {
    /** Average cycle time in minutes across all completed tasks */
    private double avgCycleTimeMinutes;
    /** Task definition key with the highest average completion time */
    private String bottleneckTaskKey;
    private double bottleneckAvgMinutes;
    /** % of tasks completed before their due date */
    private double slaCompliancePct;
    /** Total tasks sampled */
    private long totalTasksSampled;
    /** Per-task-definition breakdown */
    private List<TaskStepMetric> stepBreakdown;
    private String tenantId;

    @Data @Builder
    public static class TaskStepMetric {
        private String taskDefinitionKey;
        private long count;
        private double avgMinutes;
    }
}
