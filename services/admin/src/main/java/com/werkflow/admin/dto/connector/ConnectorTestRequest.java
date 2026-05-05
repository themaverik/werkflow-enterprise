package com.werkflow.admin.dto.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConnectorTestRequest {
    @NotBlank
    @Size(max = 500)
    private String path;

    @NotBlank
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE")
    private String method;

    @Size(max = 10240) // 10KB request body limit
    private String requestBody;

    /** Optional — target a specific environment endpoint. Falls back to production → first. */
    @Pattern(regexp = "development|staging|production")
    private String environment;
}
