package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorPathRequest {

    @NotBlank @Size(max = 500)
    private String path;

    @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE")
    private String httpMethod;

    @NotBlank @Pattern(regexp = "QUERY|ACTION|WEBHOOK_OUT")
    private String interactionType;

    @Size(max = 500)
    private String description;

    private String requestSchema;
    private String responseSchema;
    private String variableMappings;
}
