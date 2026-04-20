package com.werkflow.admin.controller;

import com.werkflow.admin.dto.RoleRequest;
import com.werkflow.admin.dto.RoleResponse;
import com.werkflow.admin.entity.Role;
import com.werkflow.admin.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role and permission management APIs")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create role", description = "Create a new role (SUPER_ADMIN only)")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID", description = "Retrieve role details by ID")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long id) {
        RoleResponse response = roleService.getRoleById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all roles", description = "Retrieve all roles")
    public ResponseEntity<List<RoleResponse>> getAllRoles() {
        List<RoleResponse> response = roleService.getAllRoles();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get roles by type", description = "Retrieve roles by type (SYSTEM, FUNCTIONAL, DEPARTMENTAL)")
    public ResponseEntity<List<RoleResponse>> getRolesByType(@PathVariable Role.RoleType type) {
        List<RoleResponse> response = roleService.getRolesByType(type);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get roles by organization", description = "Retrieve all roles for an organization")
    public ResponseEntity<List<RoleResponse>> getRolesByOrganization(@PathVariable Long organizationId) {
        List<RoleResponse> response = roleService.getRolesByOrganization(organizationId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update role", description = "Update an existing role (SUPER_ADMIN only)")
    public ResponseEntity<RoleResponse> updateRole(
        @PathVariable Long id,
        @Valid @RequestBody RoleRequest request
    ) {
        RoleResponse response = roleService.updateRole(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete role", description = "Delete a role (SUPER_ADMIN only)")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
