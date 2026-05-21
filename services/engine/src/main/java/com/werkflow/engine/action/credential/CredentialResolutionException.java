package com.werkflow.engine.action.credential;

/**
 * Thrown when a credential cannot be resolved at runtime — typically because
 * an explicit label was requested but no metadata row or Vault entry exists,
 * or because OpenBao itself is unreachable.
 *
 * <p>Distinct from {@link IllegalArgumentException} (thrown when the credential
 * <i>type</i> is not registered at all). This separation lets delegates surface
 * the right user message: "credential not configured" vs "unknown credential type".
 */
public class CredentialResolutionException extends RuntimeException {

    public CredentialResolutionException(String message) {
        super(message);
    }

    public CredentialResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
