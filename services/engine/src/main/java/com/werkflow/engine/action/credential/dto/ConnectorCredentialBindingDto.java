package com.werkflow.engine.action.credential.dto;

/**
 * The credential a registered connector is bound to (ADR-024 Model A). Engine-side mirror of
 * admin's {@code ConnectorCredentialBindingResponse}, carried over the internal
 * {@code /credential-binding} lookup.
 *
 * <p>{@code credentialType} is the canonical slug already mapped admin-side from the connector's
 * authScheme (e.g. {@code http-bearer-token}); {@code credentialRef} is the credential label.
 * The engine resolves this pair against OpenBao via {@code CredentialRegistry}.
 */
public record ConnectorCredentialBindingDto(String credentialType, String credentialRef) {}
