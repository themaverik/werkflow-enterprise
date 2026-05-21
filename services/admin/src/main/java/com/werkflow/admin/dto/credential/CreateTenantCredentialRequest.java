package com.werkflow.admin.dto.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request to register a new tenant credential.
 *
 * <p>The {@code values} map holds the secret payload — written verbatim to OpenBao
 * and never persisted to the admin DB. The server validates the credential type and
 * label slugs against {@code ^[a-z][a-z0-9-]*$} (mirrors {@code tenant_datasource.ref}).
 *
 * @param credentialType canonical credential type slug (e.g. {@code "smtp"}, {@code "slack-bot-token"})
 * @param label          tenant-chosen instance label (e.g. {@code "default"}, {@code "ops-workspace"})
 * @param values         field-name -> value map; written to Vault as the secret payload
 */
public record CreateTenantCredentialRequest(
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "credentialType must be lowercase alphanumeric with hyphens, starting with a letter")
    String credentialType,

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z][a-z0-9-]*$",
             message = "label must be lowercase alphanumeric with hyphens, starting with a letter")
    String label,

    @NotNull
    Map<String, Object> values
) {
    /** Masks {@code values} in any logged representation of this request. */
    @Override
    public String toString() {
        return "CreateTenantCredentialRequest{credentialType=" + credentialType
            + ", label=" + label
            + ", values=<redacted, " + (values == null ? 0 : values.size()) + " fields>}";
    }
}
