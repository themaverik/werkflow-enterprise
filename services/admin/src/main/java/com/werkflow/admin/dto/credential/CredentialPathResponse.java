package com.werkflow.admin.dto.credential;

/**
 * Engine-internal lookup response. Maps {@code (tenantCode, credentialType, label)}
 * to the OpenBao path that the engine should read directly.
 *
 * <p>Per ADR-020 / Phase B.2 brainstorm: admin owns the metadata index; engine reads
 * Vault directly with its own read-only token. This DTO is the only thing crossing
 * the admin/engine boundary for credential resolution — secret values stay in Vault.
 */
public record CredentialPathResponse(
    String tenantId,
    String credentialType,
    String label,
    String vaultPath
) {}
