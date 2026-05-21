package com.werkflow.engine.action.notification;

import java.util.List;

/**
 * Deprecated shim for {@link AdapterRegistry} (ADR-019).
 *
 * <p>Not registered as a Spring bean — the active bean is {@link AdapterRegistry}.
 * This class is retained only to allow unmigrated call-sites that reference
 * {@code NotificationChannelFactory} to compile without change during the transition.
 * It will be deleted once all references are gone.
 *
 * @deprecated Use {@link AdapterRegistry} directly.
 */
@Deprecated(since = "ADR-019", forRemoval = true)
public class NotificationChannelFactory extends AdapterRegistry {

    public NotificationChannelFactory(List<ServiceAdapter> adapters) {
        super(adapters);
    }
}
