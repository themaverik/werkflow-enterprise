package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleGroupMappingRequest(
    String tenantCode,  // nullable — controller resolves from JWT if absent
    @NotBlank(message = "Role name is required")
    String roleName,
    @NotBlank(message = "Group name is required")
    String groupName
) {}
