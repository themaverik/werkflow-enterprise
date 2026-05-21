package com.werkflow.admin.dto.credential;

/**
 * Response body for {@code POST /api/v1/config/credentials/{id}/test}.
 *
 * <p>Mirrors engine's {@code TestResult} record — the cross-service wire format
 * for credential test outcomes. Plaintext values never appear in this DTO;
 * only the boolean outcome and a human-readable message.
 *
 * @param success {@code true} if the credential validated; {@code false} otherwise
 * @param message {@code "OK"} on success; a human-readable failure description otherwise
 */
public record CredentialTestResultResponse(boolean success, String message) {}
