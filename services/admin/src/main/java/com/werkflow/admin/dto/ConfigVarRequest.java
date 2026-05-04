package com.werkflow.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfigVarRequest(
    String tenantCode,
    @NotBlank(message = "Variable key is required")
    String varKey,
    @NotBlank(message = "Variable value is required")
    String varValue,
    String varType,
    String description
) {
    public ConfigVarRequest {
        if (varType == null || varType.isBlank()) varType = "STRING";
    }
}
