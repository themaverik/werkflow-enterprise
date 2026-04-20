package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorRequest {
    @NotBlank
    private String tenantCode;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-_]+$", message = "connectorKey must be lowercase alphanumeric, hyphens, or underscores")
    private String connectorKey;

    @NotBlank
    @Size(max = 200)
    private String displayName;

    @NotBlank
    @Size(max = 500)
    private String baseUrl;

    @NotBlank
    @Pattern(regexp = "development|staging|production")
    private String environment;

    private boolean active = true;

    // Authentication
    @NotBlank
    @Pattern(regexp = "API_KEY|BEARER|BASIC|OAUTH2_CLIENT_CREDENTIALS|NONE")
    private String authScheme;

    @NotBlank
    @Size(max = 200)
    private String secretRef;

    @Size(max = 100)
    private String headerName;

    // Contract (optional at creation time)
    private String sampleSchema; // JSON string of parsed field list
}
