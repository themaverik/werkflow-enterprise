package com.werkflow.admin.controller;

import com.werkflow.admin.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/keycloak")
@RequiredArgsConstructor
public class KeycloakController {

    private final KeycloakUserService keycloakUserService;

    /**
     * Returns all non-internal realm roles for use in dropdown selectors
     * (Tier 2 role mapping, DOA role-level assignment).
     */
    @GetMapping("/realm-roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, List<String>>> listRealmRoles() {
        return ResponseEntity.ok(Map.of("roles", keycloakUserService.listRealmRoles()));
    }
}
