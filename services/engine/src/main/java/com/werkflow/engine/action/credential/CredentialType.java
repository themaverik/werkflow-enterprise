package com.werkflow.engine.action.credential;

import java.util.List;

/**
 * Contract for a typed credential schema (ADR-020).
 *
 * <p>Each implementation describes the fields required by one category of external system
 * (e.g. SMTP, Slack, WhatsApp). Implementations are Spring beans auto-registered by
 * {@link CredentialRegistry}.
 *
 * <p>The {@code applyTo()} method is absent here by design. In Phase B.4 it was split into
 * transport-specific sub-interfaces — {@link HttpCredentialType} for HTTP-outbound types
 * (Basic auth, header auth, API keys) and {@link DatabaseCredentialType} for JDBC pool
 * construction — because a uniform {@code applyTo(Object)} signature across transports
 * was rejected: each transport carries a fundamentally different mutable context.
 * This interface remains the shared storage and UI surface that all credential types share.
 *
 * @see CredentialRegistry
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
public interface CredentialType {

    /**
     * Canonical identifier for this credential type.
     * Used as the registry lookup key (e.g. {@code "smtp"}).
     */
    String name();

    /**
     * Human-readable label for UI rendering (e.g. {@code "SMTP Server"}).
     */
    String displayName();

    /**
     * Ordered list of fields that make up this credential schema.
     * Consumers (admin UI in B.3) use this list to render a typed form.
     */
    List<CredentialField> fields();

    /**
     * Validates that the provided values satisfy this credential's required-field contract.
     *
     * <p>In Phase B.1a this is a field-shape check only — no network connectivity tests.
     * Network validation is a Phase B.2 concern.
     *
     * @param values the credential values to validate
     * @return a {@link TestResult} indicating success or the first missing/blank field
     */
    TestResult validate(CredentialValues values);
}
