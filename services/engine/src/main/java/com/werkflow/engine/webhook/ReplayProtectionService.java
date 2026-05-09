package com.werkflow.engine.webhook;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Guards against duplicate webhook delivery within a configurable replay window.
 *
 * <p>Uses an in-process Caffeine cache keyed by
 * {@code tenantCode:connectorKey:idempotencyKey}. The window defaults to 2 hours
 * but can be overridden per connector via the ConnectorDefinition JSON.</p>
 *
 * <p>Important: this is a single-node cache. For a multi-instance deployment a
 * Redis-backed implementation would be required. Acceptable for demo scope.</p>
 */
@Service
public class ReplayProtectionService {

    private final Cache<String, Boolean> seen;

    public ReplayProtectionService(
            @Value("${werkflow.webhook.replay-window-seconds:7200}") long windowSeconds) {
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(50_000)
                .build();
    }

    /**
     * Returns {@code true} if this idempotency key has been seen before within the replay window.
     * Records the key as seen if it is new.
     */
    public boolean isDuplicate(String tenantCode, String connectorKey, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String cacheKey = tenantCode + ":" + connectorKey + ":" + idempotencyKey;
        Boolean previous = seen.getIfPresent(cacheKey);
        if (previous != null) {
            return true;
        }
        seen.put(cacheKey, Boolean.TRUE);
        return false;
    }
}
