package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of all {@link ServiceAdapter} implementations (ADR-019).
 *
 * <p>Spring collects every {@code @Component} that implements {@link ServiceAdapter} and injects
 * them as a list. The registry indexes them by {@link ServiceAdapter#name()} for O(1) lookup.
 *
 * <p>Replaces {@link NotificationChannelFactory}. The old factory is kept as a deprecated
 * Spring alias that delegates to this class so existing bean references do not break during
 * the transition period.
 *
 * @see ServiceAdapter
 * @see <a href="../../../../../../../../../../docs/adr/ADR-019-service-adapter-layer.md">ADR-019</a>
 */
@Component
public class AdapterRegistry {

    private final Map<String, ServiceAdapter> adapters;

    public AdapterRegistry(List<ServiceAdapter> adapters) {
        this.adapters = adapters.stream()
            .collect(Collectors.toMap(ServiceAdapter::name, Function.identity()));
    }

    /**
     * Returns the adapter registered under {@code name}.
     *
     * @param name adapter name (e.g. {@code "email"})
     * @return the matching {@link ServiceAdapter}
     * @throws IllegalArgumentException if no adapter is registered under {@code name}
     */
    public ServiceAdapter getAdapter(String name) {
        ServiceAdapter adapter = adapters.get(name);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown service adapter: " + name);
        }
        return adapter;
    }

    /**
     * Backward-compat alias for {@link #getAdapter(String)}.
     * Retained so call-sites using the old {@code getChannel()} method compile during transition.
     *
     * @deprecated use {@link #getAdapter(String)} instead
     */
    @Deprecated(since = "ADR-019", forRemoval = true)
    public ServiceAdapter getChannel(String name) {
        return getAdapter(name);
    }
}
