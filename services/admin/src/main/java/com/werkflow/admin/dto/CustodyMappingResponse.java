package com.werkflow.admin.dto;

import java.time.LocalDateTime;

public record CustodyMappingResponse(
    Long id,
    String tenantCode,
    String categoryKey,
    String custodyGroup,
    String displayName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
