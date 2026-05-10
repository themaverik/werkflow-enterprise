package com.werkflow.engine.action.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DatasourceRegistry} verifying tenant isolation.
 * Pool creation itself is not tested here (requires a live DB);
 * we verify cache key separation and circuit breaker identity.
 */
@ExtendWith(MockitoExtension.class)
class DatasourceRegistryTest {

    @Mock private RestTemplate serviceRestTemplate;

    private DatasourceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DatasourceRegistry(serviceRestTemplate, "http://admin:8083");
    }

    // -------------------------------------------------------------------------
    // Circuit breaker isolation
    // -------------------------------------------------------------------------

    @Test
    void circuitBreaker_differentKeysReturnDifferentInstances() {
        var cb1 = registry.circuitBreaker("tenant-a", "connector-1");
        var cb2 = registry.circuitBreaker("tenant-a", "connector-2");
        var cb3 = registry.circuitBreaker("tenant-b", "connector-1");

        assertThat(cb1.getName()).isEqualTo("tenant-a:connector-1");
        assertThat(cb2.getName()).isEqualTo("tenant-a:connector-2");
        assertThat(cb3.getName()).isEqualTo("tenant-b:connector-1");

        // Same key returns same instance (registry caches them)
        assertThat(registry.circuitBreaker("tenant-a", "connector-1"))
            .isSameAs(cb1);
    }

    @Test
    void circuitBreaker_sameKeyReturnsSameInstance() {
        var cb = registry.circuitBreaker("tenant-x", "my-connector");
        assertThat(registry.circuitBreaker("tenant-x", "my-connector")).isSameAs(cb);
    }

    @Test
    void circuitBreaker_tenantIsolation_differentTenantsHaveSeparateCircuits() {
        var cbA = registry.circuitBreaker("tenant-alpha", "invoice-db");
        var cbB = registry.circuitBreaker("tenant-beta", "invoice-db");

        // Different tenants get different circuit breakers even for the same connector key
        assertThat(cbA).isNotSameAs(cbB);
        assertThat(cbA.getName()).isNotEqualTo(cbB.getName());
    }

    // -------------------------------------------------------------------------
    // Eviction
    // -------------------------------------------------------------------------

    @Test
    void evict_doesNotThrowWhenNothingCached() {
        assertThatNoException().isThrownBy(() ->
            registry.evict("tenant-x", "nonexistent-ref"));
    }
}
