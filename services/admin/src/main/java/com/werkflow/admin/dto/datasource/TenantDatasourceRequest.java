package com.werkflow.admin.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating or updating a tenant datasource registration.
 *
 * <p>{@code credentialRef} names an existing {@code jdbc-password} credential that supplies
 * the datasource username/password. Credentials are managed via the credentials API; this
 * registration only references one.</p>
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
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "credentialRef must be lowercase alphanumeric with hyphens, starting with a letter")
    String credentialRef,

    String dialect,

    @Min(0) @Max(50)   int poolMinSize,
    @Min(1) @Max(50)   int poolMaxSize,
    @Min(1) @Max(30)   int connectionTimeoutSeconds,
    @Min(1) @Max(3600) int idleTimeoutSeconds
) {}
