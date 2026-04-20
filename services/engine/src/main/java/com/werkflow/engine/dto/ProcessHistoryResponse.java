package com.werkflow.engine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for process instance history endpoint
 * Contains timeline of events with pagination metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Process instance history with pagination")
public class ProcessHistoryResponse {

    @Schema(description = "List of historical events")
    private List<ProcessEventHistoryDTO> events;

    @Schema(description = "Total number of events", example = "15")
    private Long totalEvents;

    @Schema(description = "Current page number (zero-based)", example = "0")
    private Integer page;

    @Schema(description = "Page size", example = "20")
    private Integer size;

    @Schema(description = "Total number of pages", example = "1")
    private Integer totalPages;
}
