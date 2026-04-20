package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for starting a process instance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartProcessRequest {

    @NotBlank(message = "Process definition key is required")
    private String processDefinitionKey;

    private String businessKey;

    private Map<String, Object> variables;

    private String tenantId;
}
