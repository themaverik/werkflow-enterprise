package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorUpdateRequest {

    @NotBlank @Size(max = 200)
    private String displayName;

    @NotBlank @Size(max = 500)
    private String baseUrl;

    @NotBlank @Pattern(regexp = "development|staging|production")
    private String environment;

    private boolean active = true;

    @Pattern(regexp = "API|WEBHOOK|MCP|OTHER")
    private String connectorType;

    @NotBlank @Pattern(regexp = "API_KEY|BEARER|BASIC|NONE")
    private String authScheme;

    /**
     * Label of the OpenBao-backed credential to bind (Phase B.6). If blank, the existing
     * binding is preserved. Required (non-blank) when changing to a non-NONE authScheme
     * that has no binding yet — enforced in the service layer.
     */
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "credentialRef must be lowercase alphanumeric with hyphens, starting with a letter")
    private String credentialRef;
}
