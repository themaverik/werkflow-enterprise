package com.werkflow.engine.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProcessDraftSummaryDTO {
    private UUID id;
    private String processKey;
    private String name;
    private String departmentCode;
    private String categoryCode;
    @Builder.Default
    private List<String> tags = List.of();
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
