package com.werkflow.engine.action.db;

import com.werkflow.engine.action.credential.CredentialRegistry;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatasourceRegistry} verifying tenant isolation and credential wiring.
 * Live-DB pool creation is not tested here; we verify cache-key separation, circuit-breaker
 * identity, and the credential-type cast guard.
 */
@ExtendWith(MockitoExtension.class)
class DatasourceRegistryTest {

    @Mock private RestTemplate serviceRestTemplate;
    @Mock private CredentialRegistry credentialRegistry;

    private DatasourceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DatasourceRegistry(serviceRestTemplate, credentialRegistry, "http://admin:8083");
    }

    @Test
    void circuitBreaker_differentKeysReturnDifferentInstances() {
        var cb1 = registry.circuitBreaker("tenant-a", "connector-1");
        var cb2 = registry.circuitBreaker("tenant-a", "connector-2");
        var cb3 = registry.circuitBreaker("tenant-b", "connector-1");

        assertThat(cb1.getName()).isEqualTo("tenant-a:connector-1");
        assertThat(cb2.getName()).isEqualTo("tenant-a:connector-2");
        assertThat(cb3.getName()).isEqualTo("tenant-b:connector-1");
        assertThat(registry.circuitBreaker("tenant-a", "connector-1")).isSameAs(cb1);
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
        assertThat(cbA).isNotSameAs(cbB);
        assertThat(cbA.getName()).isNotEqualTo(cbB.getName());
    }

    @Test
    void evict_doesNotThrowWhenNothingCached() {
        assertThatNoException().isThrownBy(() -> registry.evict("tenant-x", "nonexistent-ref"));
    }

    @Test
    void resolve_throwsWhenCredentialTypeIsNotDatabaseType() {
        var config = new DatasourceConfig(
            "jdbc:h2:mem:x", "org.h2.Driver", "my-cred", "h2", 1, 3, 5, 600);
        when(serviceRestTemplate.getForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(DatasourceConfig.class),
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("ds1")))
            .thenReturn(config);
        when(credentialRegistry.resolveForTenant("jdbc-password", "tenant-a", "my-cred"))
            .thenReturn(CredentialValues.of(Map.of("username", "sa", "password", "pw")));
        CredentialType notDb = new CredentialType() {
            public String name() { return "jdbc-password"; }
            public String displayName() { return "x"; }
            public List<com.werkflow.engine.action.credential.CredentialField> fields() { return List.of(); }
            public TestResult validate(CredentialValues v) { return TestResult.ok(); }
        };
        when(credentialRegistry.get("jdbc-password")).thenReturn(notDb);

        assertThatThrownBy(() -> registry.resolve("tenant-a", "ds1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DatabaseCredentialType");
    }
}
