package com.werkflow.engine.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ProcessStatsDTO {
    private long total;
    private long running;
    private long completed;
    private long failed;
    private long suspended;
    /** Average duration in minutes for completed process instances */
    private double avgDurationMinutes;
    /** completed / (completed + terminated) * 100 */
    private double successRate;
    private String tenantId;
}
