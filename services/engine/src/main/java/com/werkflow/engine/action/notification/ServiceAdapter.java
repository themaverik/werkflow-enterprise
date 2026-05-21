package com.werkflow.engine.action.notification;

import java.util.Set;

/**
 * Service adapter contract (ADR-019).
 *
 * <p>Every concrete delivery adapter (email, slack, whatsapp, …) implements this interface.
 * The three methods beyond {@link #send} provide the registry and future credential layer
 * (ADR-020) with the metadata they need without requiring reflection or external config:
 *
 * <ul>
 *   <li>{@link #name()} — canonical lookup key used by {@link AdapterRegistry}</li>
 *   <li>{@link #supportedOperations()} — set of operation strings the adapter can fulfil;
 *       currently {@code {"SEND_NOTIFICATION"}} for all notification adapters</li>
 *   <li>{@link #credentialTypeName()} — opaque string identifying the credential schema
 *       required by this adapter; used by ADR-020 when the credential store is introduced</li>
 * </ul>
 *
 * <p>Backward-compat shim: {@link #getChannelName()} delegates to {@link #name()} so that
 * any surviving call-sites on the old interface compile without change during the transition.
 *
 * @see AdapterRegistry
 * @see <a href="../../../../../../../../../../docs/adr/ADR-019-service-adapter-layer.md">ADR-019</a>
 */
public interface ServiceAdapter {

    /** Canonical adapter name used as the registry lookup key (e.g. {@code "email"}). */
    String name();

    /**
     * Set of operation identifiers this adapter can handle.
     * Notification adapters return {@code Set.of("SEND_NOTIFICATION")}.
     */
    Set<String> supportedOperations();

    /**
     * Opaque identifier for the credential schema this adapter requires.
     * Returns a hardcoded string per adapter until ADR-020 introduces the credential store.
     * Example: {@code "smtp"}, {@code "slack-bot-token"}, {@code "whatsapp-cloud-api"}.
     */
    String credentialTypeName();

    /**
     * Dispatch a notification request through this adapter.
     *
     * @param request the fully-rendered notification payload
     */
    void send(ActionBlockNotificationRequest request);

    /**
     * Backward-compat alias for {@link #name()}.
     * Retained so old call-sites referencing {@code getChannelName()} compile during transition.
     *
     * @deprecated use {@link #name()} instead
     */
    @Deprecated(since = "ADR-019", forRemoval = true)
    default String getChannelName() {
        return name();
    }
}
