package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Paginated response wrapper for task lists
 * Includes HATEOAS pagination links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListResponse {

    private List<TaskResponse> content;
    private PageInfo page;
    private Map<String, String> links;

    /**
     * Pagination metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private int size;
        private int number;
        private long totalElements;
        private int totalPages;
    }
}
