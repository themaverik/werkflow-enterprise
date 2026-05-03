package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorUpdateRequest {

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

    @NotBlank
    @Pattern(regexp = "API_KEY|BEARER|BASIC|OAUTH2_CLIENT_CREDENTIALS|NONE")
    private String authScheme;

    /** If blank, the existing secretRef is preserved. */
    @Size(max = 200)
    private String secretRef;

    @Size(max = 100)
    private String headerName;
}
