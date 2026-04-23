package com.werkflow.engine.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserEmailResolverTest {

    private Keycloak keycloak;
    private RealmResource realmResource;
    private UsersResource usersResource;
    private UserEmailResolver resolver;

    @BeforeEach
    void setUp() {
        keycloak = mock(Keycloak.class);
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        when(keycloak.realm("werkflow")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        resolver = new UserEmailResolver(keycloak, "werkflow");
    }

    @Test
    void resolveEmail_returnsEmail_whenUserExists() {
        UserRepresentation user = new UserRepresentation();
        user.setEmail("jane.employee@werkflow.local");
        when(usersResource.searchByUsername("jane.employee", true)).thenReturn(List.of(user));

        Optional<String> result = resolver.resolveEmail("jane.employee");

        assertThat(result).contains("jane.employee@werkflow.local");
    }

    @Test
    void resolveEmail_returnsEmpty_whenUserNotFound() {
        when(usersResource.searchByUsername("unknown", true)).thenReturn(List.of());

        Optional<String> result = resolver.resolveEmail("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveEmail_returnsEmpty_whenUsernameIsNull() {
        Optional<String> result = resolver.resolveEmail(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(usersResource);
    }

    @Test
    void resolveEmail_cachesResult_andDoesNotCallKeycloakTwice() {
        UserRepresentation user = new UserRepresentation();
        user.setEmail("jane.employee@werkflow.local");
        when(usersResource.searchByUsername("jane.employee", true)).thenReturn(List.of(user));

        resolver.resolveEmail("jane.employee");
        resolver.resolveEmail("jane.employee");

        verify(usersResource, times(1)).searchByUsername("jane.employee", true);
    }

    @Test
    void resolveEmail_doesNotCache_whenKeycloakThrows() {
        when(usersResource.searchByUsername("jane.employee", true))
            .thenThrow(new RuntimeException("Keycloak unavailable"));

        Optional<String> first = resolver.resolveEmail("jane.employee");
        assertThat(first).isEmpty();

        // Second call must re-query Keycloak — failure must not be cached
        resolver.resolveEmail("jane.employee");
        verify(usersResource, times(2)).searchByUsername("jane.employee", true);
    }

    @Test
    void resolveEmail_returnsEmpty_whenUsernameIsBlank() {
        Optional<String> result = resolver.resolveEmail("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(usersResource);
    }
}
