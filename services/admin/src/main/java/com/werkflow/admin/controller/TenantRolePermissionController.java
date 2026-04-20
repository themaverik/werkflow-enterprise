package com.werkflow.admin.controller;

import com.werkflow.admin.service.TenantRolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/internal/tenants")
@RequiredArgsConstructor
public class TenantRolePermissionController {

    private final TenantRolePermissionService service;

    /**
     * Used by engine-service WerkflowPermissionEvaluator to resolve tenant-specific permissions.
     * Query params: roles=role1,role2
     */
    @GetMapping("/{tenantCode}/role-permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Set<String>> getPermissions(
            @PathVariable String tenantCode,
            @RequestParam List<String> roles) {
        return ResponseEntity.ok(service.getPermissionsForRoles(tenantCode, roles));
    }
}
