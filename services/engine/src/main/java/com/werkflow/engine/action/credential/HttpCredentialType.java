package com.werkflow.engine.action.credential;

import java.net.http.HttpRequest;

/**
 * Transport-specific sub-interface of {@link CredentialType} for HTTP-outbound credentials
 * (ADR-020 Phase B.4).
 *
 * <p>This interface was deferred from {@link CredentialType}'s original design because a
 * uniform {@code applyTo(Object)} signature was rejected in favour of transport-specific
 * sub-interfaces — each transport carries a different mutable request context, so a typed
 * parameter surface is safer and clearer than a raw {@code Object} or marker interface.
 *
 * <p>Implementations mutate the supplied {@link HttpRequest.Builder} to inject the
 * appropriate authorization material — for example:
 * <ul>
 *   <li>Basic auth → sets the {@code Authorization: Basic ...} header</li>
 *   <li>Header auth → sets an arbitrary named header (e.g. {@code X-Api-Key})</li>
 *   <li>SendGrid / third-party API keys → sets {@code Authorization: Bearer ...}</li>
 * </ul>
 *
 * <p>Implementations are Spring beans auto-registered by {@link CredentialRegistry} in the
 * same way as plain {@link CredentialType} beans. Because this interface extends
 * {@link CredentialType}, every HTTP credential also participates in the storage and UI
 * surface (name, displayName, fields, validate).
 *
 * @see CredentialType
 * @see DatabaseCredentialType
 * @see <a href="../../../../../../../../../../docs/adr/ADR-020-credential-types-as-peer-concept.md">ADR-020</a>
 */
public interface HttpCredentialType extends CredentialType {

    /**
     * Applies this credential's authorization material to the given HTTP request builder.
     *
     * <p>Implementations MUST NOT make network calls here — this method is on the hot path
     * of every HTTP connector invocation and must remain synchronous and fast.
     *
     * @param builder the {@link HttpRequest.Builder} to mutate; never {@code null}
     * @param values  the resolved credential values for this invocation; never {@code null}
     */
    void applyTo(HttpRequest.Builder builder, CredentialValues values);
}
