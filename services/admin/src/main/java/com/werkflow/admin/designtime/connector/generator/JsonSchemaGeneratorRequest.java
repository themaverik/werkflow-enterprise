package com.werkflow.admin.designtime.connector.generator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the JSON Schema connector generator.
 */
public record JsonSchemaGeneratorRequest(

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "connectorKey must be lowercase alphanumeric with hyphens")
    String connectorKey,

    @NotBlank
    String displayName,

    /** A valid JSON Schema document string. */
    @NotBlank
    String schemaJson
) {}
