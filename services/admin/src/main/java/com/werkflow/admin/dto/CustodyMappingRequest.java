package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CustodyMappingRequest(
    @NotBlank(message = "Tenant code is required")
    String tenantCode,
    @NotBlank(message = "Category key is required")
    String categoryKey,
    @NotBlank(message = "Custody group is required")
    String custodyGroup,
    String displayName
) {}
