package com.werkflow.admin.controller;

import com.werkflow.admin.dto.ConfigVarRequest;
import com.werkflow.admin.dto.ConfigVarResponse;
import com.werkflow.admin.service.ConfigurationVariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/vars")
@RequiredArgsConstructor
public class ConfigurationVariableController {

    private final ConfigurationVariableService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConfigVarResponse>> list(
            @RequestParam String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        enforceTenantOwnership(jwt, tenantCode);
        return ResponseEntity.ok(service.listByTenant(tenantCode));
    }

    /** Returns key→value map for FEEL context injection (internal use by engine).
     * MED-05: caller's tenant must match the requested tenantCode unless SUPER_ADMIN.
     */
    @GetMapping("/map")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> varMap(
            @RequestParam String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        enforceTenantOwnership(jwt, tenantCode);
        return ResponseEntity.ok(service.getVarMap(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> create(@Valid @RequestBody ConfigVarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ConfigVarRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * MED-05: enforces that non-SUPER_ADMIN callers can only access their own tenant's config vars.
     * SUPER_ADMIN may access any tenant.
     */
    private void enforceTenantOwnership(Jwt jwt, String requestedTenantCode) {
        // Check for SUPER_ADMIN role in realm_access
        java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof java.util.Collection<?> r
                    && (r.contains("SUPER_ADMIN") || r.contains("super_admin"))) {
                return; // SUPER_ADMIN bypass
            }
        }
        String callerTenant = jwt.getClaimAsString("tenant_code");
        if (callerTenant == null || callerTenant.isBlank()) {
            callerTenant = "default";
        }
        if (!callerTenant.equals(requestedTenantCode)) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Access denied: tenant mismatch");
        }
    }
}
