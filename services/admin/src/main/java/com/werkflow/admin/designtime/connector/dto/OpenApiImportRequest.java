package com.werkflow.admin.designtime.connector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/connectors/import-openapi}.
 * Either {@code content} (raw YAML/JSON string) or {@code url} (resolvable OpenAPI source)
 * must be provided; validation is enforced in the service layer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenApiImportRequest(
        /**
         * Raw OpenAPI 3.1 YAML or JSON string. Mutually exclusive with {@code url}.
         * At least one of {@code content} or {@code url} is required.
         */
        String content,

        /**
         * URL pointing to an OpenAPI 3.1 document. Resolved server-side.
         * Must use HTTPS in production; validated by SsrfGuard.
         */
        String url,

        /** Stable connector key to register under. Defaults to the OpenAPI info.title slug. */
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,62}[a-z0-9]$",
                 message = "key must be lowercase kebab-case, 3–64 characters")
        String connectorKey,

        /** Override display name. Defaults to OpenAPI info.title. */
        String displayName
) {}
