package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorEndpointRequest {

    @NotBlank @Size(max = 500)
    private String baseUrl;

    @NotBlank @Pattern(regexp = "development|staging|production")
    private String environment;

    @Pattern(regexp = "API|WEBHOOK|MCP|OTHER")
    private String connectorType = "API";

    private boolean active = true;
}
