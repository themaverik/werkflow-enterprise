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
    @Pattern(regexp = "^[a-z][a-z0-9-_]*$", message = "connectorKey must start with a lowercase letter and contain only lowercase alphanumerics, hyphens, or underscores")
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

    @NotBlank @Pattern(regexp = "API_KEY|BEARER|BASIC|NONE")
    private String authScheme;

    /**
     * Label of the OpenBao-backed credential to bind to this connector (Phase B.6).
     * Required unless authScheme is NONE. The credential's type must match authScheme.
     */
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "credentialRef must be lowercase alphanumeric with hyphens, starting with a letter")
    private String credentialRef;

    private String sampleSchema;
}
