package com.werkflow.admin.dto.connector;

/**
 * The credential a registered connector is bound to, resolved for the engine (ADR-024 Model A).
 *
 * <p>Returned by the internal {@code /credential-binding} endpoint so the engine can apply the
 * connector's own authentication to connector-mode service tasks. Carries only the
 * already-mapped credential-type slug (admin owns the {@code authScheme → type} mapping) and the
 * credential label ({@code credentialRef}) — never any secret material. A connector with
 * {@code authScheme=NONE} or no bound credential has no binding (the endpoint returns 204).
 */
public record ConnectorCredentialBindingResponse(String credentialType, String credentialRef) {}
