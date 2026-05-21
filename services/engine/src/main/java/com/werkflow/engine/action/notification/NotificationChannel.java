package com.werkflow.engine.action.notification;

/**
 * Deprecated alias for {@link ServiceAdapter} (ADR-019).
 *
 * <p>Retained only to prevent compilation failures in unmigrated call-sites.
 * Will be removed once all references are gone.
 *
 * @deprecated Use {@link ServiceAdapter} directly.
 */
@Deprecated(since = "ADR-019", forRemoval = true)
public interface NotificationChannel extends ServiceAdapter {
}
