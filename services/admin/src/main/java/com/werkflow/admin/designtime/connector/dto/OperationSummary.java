package com.werkflow.admin.designtime.connector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata for a single operation in a ConnectorDefinition.
 * Returned by {@code GET /api/v1/design/connectors/{key}/operations}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationSummary(
        String id,
        String displayName,
        String description,
        String category,
        boolean deprecated,
        boolean hasPagination,
        boolean hasInput,
        boolean hasOutput
) {}
