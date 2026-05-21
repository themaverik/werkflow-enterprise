package com.werkflow.admin.dto.credential;

import com.werkflow.admin.entity.TenantCredential;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Metadata response for a registered tenant credential.
 *
 * <p>Plaintext field values are <b>never</b> returned. The optional {@code fieldNames}
 * list reflects only which keys are set in Vault (echoed from the request on
 * create/update). This lets the portal display "Slack credential — botToken, signingSecret"
 * without ever leaking secret material.
 *
 * <p>The internal {@code vaultPath} is intentionally omitted from this tenant-facing
 * DTO — it leaks the Vault path layout to any role that can read credentials.
 * Engine receives the path via {@link CredentialPathResponse} on the
 * {@code ENGINE_SERVICE}-gated endpoint instead.
 */
public record TenantCredentialResponse(
    UUID id,
    String tenantId,
    String credentialType,
    String label,
    List<String> fieldNames,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime rotatedAt
) {

    public static TenantCredentialResponse from(TenantCredential entity, List<String> fieldNames) {
        return new TenantCredentialResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getCredentialType(),
            entity.getLabel(),
            fieldNames,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getRotatedAt()
        );
    }
}
