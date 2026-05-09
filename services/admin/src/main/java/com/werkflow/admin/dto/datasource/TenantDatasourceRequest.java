package com.werkflow.admin.dto.datasource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating or updating a tenant datasource registration.
 */
public record TenantDatasourceRequest(

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "ref must be lowercase alphanumeric with hyphens, starting with a letter")
    String ref,

    @NotBlank
    String jdbcUrl,

    @NotBlank
    String driverClassName,

    @NotBlank
    String username,

    /**
     * Key reference into the secrets manager — NOT the raw password.
     * Example: {@code vault://secret/tenants/acme/db/hris}.
     */
    @NotBlank
    String passwordSecretRef,

    String dialect,

    @Min(0) int poolMinSize,
    @Min(1) int poolMaxSize,
    @Min(1) int connectionTimeoutSeconds,
    @Min(1) int idleTimeoutSeconds
) {}
