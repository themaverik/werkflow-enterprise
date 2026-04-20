package com.werkflow.engine.security.guard;

import com.werkflow.engine.security.KeycloakRoleExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// LENIENT: @BeforeEach stub (auth.getPrincipal) is unused in nonApproveAction_returnsTrue
// which short-circuits before touching auth; approve_nullDoaLevel_returnsFalse early-returns
// before the REST call so that stub is also unused — acceptable tradeoff over duplicating stubs
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetRequestGuardTest {

    @Mock private KeycloakRoleExtractor roleExtractor;
    @Mock private RestTemplate restTemplate;
    @Mock private Authentication auth;
    @Mock private Jwt jwt;

    private AssetRequestGuard guard;

    @BeforeEach
    void setUp() {
        when(auth.getPrincipal()).thenReturn(jwt);
        // businessServiceUrl is injected via constructor — construct manually
        guard = new AssetRequestGuard(roleExtractor, restTemplate, "http://localhost:8084");
    }

    @Test
    void approve_userIsLineManagerAndSufficientDoaLevel_returnsTrue() {
        when(roleExtractor.getUserId(jwt)).thenReturn("manager-1");
        when(roleExtractor.getDoaLevel(jwt)).thenReturn(2);
        when(restTemplate.getForObject(
            contains("/api/inventory/asset-requests/req-1/approval-context"),
            eq(Map.class)
        )).thenReturn(Map.of("submitterManagerId", "manager-1", "requiredDoaLevel", 2));

        assertThat(guard.canAct(auth, "req-1", "APPROVE")).isTrue();
    }

    @Test
    void approve_userIsNotLineManager_returnsFalse() {
        when(roleExtractor.getUserId(jwt)).thenReturn("other-user");
        when(roleExtractor.getDoaLevel(jwt)).thenReturn(3);
        when(restTemplate.getForObject(
            contains("/api/inventory/asset-requests/req-1/approval-context"),
            eq(Map.class)
        )).thenReturn(Map.of("submitterManagerId", "manager-1", "requiredDoaLevel", 2));

        assertThat(guard.canAct(auth, "req-1", "APPROVE")).isFalse();
    }

    @Test
    void approve_insufficientDoaLevel_returnsFalse() {
        when(roleExtractor.getUserId(jwt)).thenReturn("manager-1");
        when(roleExtractor.getDoaLevel(jwt)).thenReturn(1);
        when(restTemplate.getForObject(
            contains("/api/inventory/asset-requests/req-1/approval-context"),
            eq(Map.class)
        )).thenReturn(Map.of("submitterManagerId", "manager-1", "requiredDoaLevel", 2));

        assertThat(guard.canAct(auth, "req-1", "APPROVE")).isFalse();
    }

    @Test
    void approve_nullDoaLevel_returnsFalse() {
        when(roleExtractor.getUserId(jwt)).thenReturn("manager-1");
        when(roleExtractor.getDoaLevel(jwt)).thenReturn(null);
        when(restTemplate.getForObject(
            contains("/api/inventory/asset-requests/req-1/approval-context"),
            eq(Map.class)
        )).thenReturn(Map.of("submitterManagerId", "manager-1", "requiredDoaLevel", 2));

        assertThat(guard.canAct(auth, "req-1", "APPROVE")).isFalse();
    }

    @Test
    void nonApproveAction_returnsTrue() {
        // Non-APPROVE actions pass through — coarse gate already checked
        assertThat(guard.canAct(auth, "req-1", "SUBMIT")).isTrue();
        verifyNoInteractions(restTemplate);
    }
}
