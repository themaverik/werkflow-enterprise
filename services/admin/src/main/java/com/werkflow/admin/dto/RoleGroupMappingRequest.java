package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleGroupMappingRequest(
    @NotBlank(message = "Tenant code is required")
    String tenantCode,
    @NotBlank(message = "Role name is required")
    String roleName,
    @NotBlank(message = "Group name is required")
    String groupName
) {}
