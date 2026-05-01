package com.werkflow.admin.dto;

import java.time.LocalDateTime;

public record ConfigVarResponse(
    Long id,
    String tenantCode,
    String varKey,
    String varValue,
    String varType,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
