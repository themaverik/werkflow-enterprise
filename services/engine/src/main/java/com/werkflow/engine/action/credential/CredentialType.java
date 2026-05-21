package com.werkflow.engine.action.credential;

import java.util.List;

/**
 * Contract for a typed credential schema (ADR-020).
 *
 * <p>Each implementation describes the fields required by one category of external system
 * (e.g. SMTP, Slack, WhatsApp). Implementations are Spring beans auto-registered by
 * {@link CredentialRegistry}.
 *
 * <p>The {@code applyTo()} method is intentionally absent here. It is deferred to Phase B.4
 * when REST/DB/Webhook adapters are refactored, because each transport has a different
 * request type (jakarta.mail.Session, RestTemplate, HTTP client), making a uniform
 * applyTo() signature speculative at this stage.
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
