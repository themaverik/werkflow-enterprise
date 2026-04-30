package com.werkflow.admin.controller;

import com.werkflow.admin.dto.RoleGroupMappingRequest;
import com.werkflow.admin.dto.RoleGroupMappingResponse;
import com.werkflow.admin.service.RoleGroupMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/role-mappings")
@RequiredArgsConstructor
public class RoleGroupMappingController {

    private final RoleGroupMappingService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleGroupMappingResponse>> list(@RequestParam String tenantCode) {
        return ResponseEntity.ok(service.listByTenant(tenantCode));
    }

    /**
     * Returns role→groups map for the engine's AdminServiceClient (internal use).
     */
    @GetMapping("/by-role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, List<String>>> byRole(@RequestParam String tenantCode) {
        return ResponseEntity.ok(service.getGroupsByRole(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleGroupMappingResponse> create(@Valid @RequestBody RoleGroupMappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
