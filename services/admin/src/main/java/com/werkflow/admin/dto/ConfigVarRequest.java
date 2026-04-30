package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfigVarRequest(
    @NotBlank(message = "Tenant code is required")
    String tenantCode,
    @NotBlank(message = "Variable key is required")
    String varKey,
    @NotBlank(message = "Variable value is required")
    String varValue,
    @Pattern(regexp = "STRING|NUMBER|BOOLEAN", message = "varType must be STRING, NUMBER, or BOOLEAN")
    String varType,
    String description
) {
    public ConfigVarRequest {
        if (varType == null || varType.isBlank()) varType = "STRING";
    }
}
