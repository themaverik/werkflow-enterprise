package com.werkflow.engine.action.credential.dto;

/**
 * Engine-side mirror of admin's {@code CredentialPathResponse}. Carried over the
 * internal lookup wire and used by the resolver to address OpenBao.
 *
 * <p>Field naming matches the JSON contract emitted by admin's
 * {@code TenantCredentialController#resolveForEngine}.
 */
public record CredentialPathDto(
    String tenantId,
    String credentialType,
    String label,
    String vaultPath
) {}
