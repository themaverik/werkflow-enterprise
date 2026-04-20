package com.werkflow.engine.security.guard;

import com.werkflow.engine.security.KeycloakRoleExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HubManagerGuardTest {

    @Mock private KeycloakRoleExtractor roleExtractor;
    @Mock private Authentication auth;
    @Mock private Jwt jwt;

    @InjectMocks
    private HubManagerGuard guard;

    @BeforeEach
    void setUp() {
        when(auth.getPrincipal()).thenReturn(jwt);
    }

    @Test
    void centralHubManager_bypassesHubIdCheck_returnsTrue() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("CENTRAL_HUB_MANAGER"));
        // getHubId is NOT called — early return fires before it

        assertThat(guard.canAct(auth, "hub-1", "MANAGE_HUB")).isTrue();
    }

    @Test
    void hubManager_matchingHubId_returnsTrue() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("HUB_MANAGER"));
        when(roleExtractor.getHubId(jwt)).thenReturn("hub-1");

        assertThat(guard.canAct(auth, "hub-1", "MANAGE_HUB")).isTrue();
    }

    @Test
    void hubManager_mismatchedHubId_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("HUB_MANAGER"));
        when(roleExtractor.getHubId(jwt)).thenReturn("hub-2");

        assertThat(guard.canAct(auth, "hub-1", "MANAGE_HUB")).isFalse();
    }

    @Test
    void hubManager_nullHubId_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("HUB_MANAGER"));
        when(roleExtractor.getHubId(jwt)).thenReturn(null);

        assertThat(guard.canAct(auth, "hub-1", "MANAGE_HUB")).isFalse();
    }
}
