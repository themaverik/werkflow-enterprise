package com.werkflow.engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Comprehensive response DTO for process monitoring
 * Combines process instance details, task history, and timeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Comprehensive process monitoring information")
public class ProcessMonitoringResponse {

    @Schema(description = "Process instance details")
    private ProcessInstanceDTO processInstance;

    @Schema(description = "List of all tasks (active and completed)")
    private List<ProcessTaskHistoryDTO> tasks;

    @Schema(description = "Timeline of historical events")
    private List<ProcessEventHistoryDTO> timeline;
}
