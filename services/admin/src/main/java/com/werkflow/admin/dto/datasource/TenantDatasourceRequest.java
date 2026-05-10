package com.werkflow.admin.dto.datasource;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating or updating a tenant datasource registration.
 *
 * <p>On create, {@code password} is required. On update, it may be omitted
 * (null or blank) to keep the existing value — the service skips the field when null.</p>
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
     * Plaintext password supplied by the user. The admin service encrypts this
     * value using AES-256-GCM (via {@link com.werkflow.common.security.EncryptionService})
     * before persisting — the raw value is never stored.
     * May be null or blank on update to retain the existing encrypted value.
     */
    @Nullable
    String password,

    String dialect,

    @Min(0) @Max(50)   int poolMinSize,
    @Min(1) @Max(50)   int poolMaxSize,
    @Min(1) @Max(30)   int connectionTimeoutSeconds,
    @Min(1) @Max(3600) int idleTimeoutSeconds
) {}
