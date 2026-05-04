package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorRequest {

    /** Optional — if omitted, resolved from the caller's JWT tenant_id claim by the controller. */
    private String tenantCode;

    @NotBlank @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-_]+$", message = "connectorKey must be lowercase alphanumeric, hyphens, or underscores")
    private String connectorKey;

    @NotBlank @Size(max = 200)
    private String displayName;

    @NotBlank @Size(max = 500)
    private String baseUrl;

    @NotBlank @Pattern(regexp = "development|staging|production")
    private String environment;

    private boolean active = true;

    @Pattern(regexp = "API|WEBHOOK|MCP|OTHER")
    private String connectorType = "API";

    @NotBlank @Pattern(regexp = "API_KEY|BEARER|BASIC|OAUTH2_CLIENT_CREDENTIALS|NONE")
    private String authScheme;

    /** Raw credential value — encrypted at rest. Required unless authScheme is NONE. */
    @Size(max = 500)
    private String secretValue;

    @Size(max = 100)
    private String headerName;

    private String sampleSchema;
}
