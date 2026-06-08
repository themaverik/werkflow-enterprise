package com.werkflow.admin.controller;

import com.werkflow.admin.designtime.platform.service.CandidateGroupsAggregator;
import com.werkflow.admin.dto.RoleGroupMappingRequest;
import com.werkflow.admin.dto.RoleGroupMappingResponse;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.RoleGroupMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/role-mappings")
@RequiredArgsConstructor
public class RoleGroupMappingController {

    private final RoleGroupMappingService service;
    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final CandidateGroupsAggregator candidateGroupsAggregator;

    private String resolveTenant(String tenantCode, Jwt jwt) {
        String jwtTenant = jwtClaimsExtractor.getTenantId(jwt);
        if (tenantCode != null && !tenantCode.isBlank() && !tenantCode.equals(jwtTenant)) {
            if (!jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN")) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "SUPER_ADMIN role required to access another tenant's data");
            }
            return tenantCode;
        }
        return jwtTenant;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleGroupMappingResponse>> list(
            @RequestParam(required = false) String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.listByTenant(resolveTenant(tenantCode, jwt)));
    }

    /**
     * Returns role→groups map for the engine's AdminServiceClient (internal use).
     */
    @GetMapping("/by-role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, List<String>>> byRole(
            @RequestParam(required = false) String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.getGroupsByRole(resolveTenant(tenantCode, jwt)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleGroupMappingResponse> create(
            @Valid @RequestBody RoleGroupMappingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenant = resolveTenant(request.tenantCode(), jwt);
        RoleGroupMappingRequest resolved = new RoleGroupMappingRequest(tenant, request.roleName(), request.groupName());
        RoleGroupMappingResponse result = service.create(resolved);
        candidateGroupsAggregator.evict(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Updates the manager-tier flag on a role-group mapping (ADR-010 visibility policy). */
    @PutMapping("/{id}/manager-tier")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleGroupMappingResponse> setManagerTier(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean flag = body.get("isManagerTier");
        if (flag == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.setManagerTier(id, flag));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String tenantCode = service.delete(id);
        candidateGroupsAggregator.evict(tenantCode);
        return ResponseEntity.noContent().build();
    }
}
