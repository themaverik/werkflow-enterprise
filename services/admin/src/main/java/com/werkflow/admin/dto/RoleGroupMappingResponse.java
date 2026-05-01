package com.werkflow.admin.dto;

import java.time.LocalDateTime;

public record RoleGroupMappingResponse(
    Long id,
    String tenantCode,
    String roleName,
    String groupName,
    LocalDateTime createdAt
) {}
