package com.werkflow.admin.controller;

import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.TenantRolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/internal/tenants")
@RequiredArgsConstructor
public class TenantRolePermissionController {

    private final TenantRolePermissionService service;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Used by engine-service WerkflowPermissionEvaluator to resolve tenant-specific permissions.
     * Query params: roles=role1,role2
     */
    @GetMapping("/{tenantCode}/role-permissions")
    @PreAuthorize("hasAnyRole('ENGINE_SERVICE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Set<String>> getPermissions(
            @PathVariable String tenantCode,
            @RequestParam List<String> roles,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isSuperAdmin = jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN");
        boolean isEngineService = jwtClaimsExtractor.hasRole(jwt, "ENGINE_SERVICE");
        if (!isSuperAdmin && !isEngineService) {
            String callerTenant = jwtClaimsExtractor.getTenantId(jwt);
            if (!tenantCode.equals(callerTenant)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access to another tenant's role permissions is not permitted");
            }
        }
        return ResponseEntity.ok(service.getPermissionsForRoles(tenantCode, roles));
    }
}
