package com.werkflow.admin.designtime.connector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight connector listing entry returned by
 * {@code GET /api/v1/design/connectors}.
 * Contains metadata only — no definition JSON, no secrets.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectorSummary(
        String key,
        String displayName,
        String description,
        String version,
        String category,
        List<String> tags,
        String transportType,
        int operationCount,
        LocalDateTime updatedAt
) {}
