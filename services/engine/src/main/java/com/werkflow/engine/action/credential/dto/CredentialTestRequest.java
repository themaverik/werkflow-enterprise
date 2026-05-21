package com.werkflow.engine.action.credential.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/internal/credentials/test}.
 * Called by admin-service to verify a tenant credential without needing
 * to ship the secret values across the wire.
 *
 * @param tenantId       owning tenant
 * @param credentialType registered credential type name (e.g. {@code "smtp"})
 * @param label          credential instance label (e.g. {@code "default"})
 */
public record CredentialTestRequest(
    @NotBlank String tenantId,
    @NotBlank String credentialType,
    @NotBlank String label
) {}
