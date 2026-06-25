package com.werkflow.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that UserController GET endpoints carry @PreAuthorize(ADMIN/SUPER_ADMIN).
 * Guards against accidental removal during refactoring.
 */
class UserControllerAuthTest {

    private static final String EXPECTED = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    @Test
    void getUserById_hasAdminGuard() throws Exception {
        assertPreAuthorize(UserController.class.getMethod("getUserById", Long.class, Jwt.class));
    }

    @Test
    void getUserByKeycloakId_hasAdminGuard() throws Exception {
        assertPreAuthorize(UserController.class.getMethod("getUserByKeycloakId", String.class, Jwt.class));
    }

    @Test
    void getUserByUsername_hasAdminGuard() throws Exception {
        assertPreAuthorize(UserController.class.getMethod("getUserByUsername", String.class, Jwt.class));
    }

    @Test
    void getUsersByOrganization_hasAdminGuard() throws Exception {
        assertPreAuthorize(UserController.class.getMethod("getUsersByOrganization", Long.class, Jwt.class));
    }

    private void assertPreAuthorize(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
            .as("@PreAuthorize missing on " + method.getName())
            .isNotNull();
        assertThat(annotation.value())
            .as("@PreAuthorize value on " + method.getName())
            .isEqualTo(EXPECTED);
    }
}
