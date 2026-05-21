package com.werkflow.admin.dto.credential;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request to rotate a tenant credential. Only the value payload changes;
 * the credential type and label are immutable after creation.
 *
 * @param values field-name -> value map; replaces the current Vault payload entirely
 */
public record UpdateTenantCredentialRequest(
    @NotNull
    Map<String, Object> values
) {
    /** Masks {@code values} in any logged representation of this request. */
    @Override
    public String toString() {
        return "UpdateTenantCredentialRequest{values=<redacted, "
            + (values == null ? 0 : values.size()) + " fields>}";
    }
}
