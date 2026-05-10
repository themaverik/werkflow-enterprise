package com.werkflow.admin.controller;

import com.werkflow.admin.designtime.platform.service.LocaleProjector;
import com.werkflow.admin.designtime.platform.service.VisibilityPolicyProjector;
import com.werkflow.admin.dto.ConfigVarRequest;
import com.werkflow.admin.dto.ConfigVarResponse;
import com.werkflow.admin.security.JwtClaimsExtractor;
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
    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final LocaleProjector localeProjector;
    private final VisibilityPolicyProjector visibilityPolicyProjector;

    private String resolveTenant(String tenantCode, Jwt jwt) {
        return (tenantCode != null && !tenantCode.isBlank()) ? tenantCode : jwtClaimsExtractor.getTenantId(jwt);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConfigVarResponse>> list(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal Jwt jwt) {
        String resolved = resolveTenant(tenantCode, jwt);
        enforceTenantOwnership(jwt, resolved);
        List<ConfigVarResponse> result = (type != null && !type.isBlank())
            ? service.listByTenantAndType(resolved, type)
            : service.listByTenant(resolved);
        return ResponseEntity.ok(result);
    }

    /** Returns key→value map for FEEL context injection (internal use by engine).
     * MED-05: caller's tenant must match the requested tenantCode unless SUPER_ADMIN.
     */
    @GetMapping("/map")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> varMap(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        String resolved = resolveTenant(tenantCode, jwt);
        enforceTenantOwnership(jwt, resolved);
        return ResponseEntity.ok(service.getVarMap(resolved));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> create(
            @Valid @RequestBody ConfigVarRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ConfigVarRequest resolved = resolveRequestTenant(request, jwt);
        ConfigVarResponse result = service.create(resolved);
        evictPssCache(resolved);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ConfigVarRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ConfigVarRequest resolved = resolveRequestTenant(request, jwt);
        ConfigVarResponse result = service.update(id, resolved);
        evictPssCache(resolved);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void evictPssCache(ConfigVarRequest request) {
        String tenant = request.tenantCode();
        if ("LOCALE".equals(request.varType())) {
            localeProjector.evict(tenant);
        } else if ("POLICY".equals(request.varType())) {
            visibilityPolicyProjector.evict(tenant);
        }
    }

    private ConfigVarRequest resolveRequestTenant(ConfigVarRequest request, Jwt jwt) {
        if (request.tenantCode() != null && !request.tenantCode().isBlank()) return request;
        return new ConfigVarRequest(
            jwtClaimsExtractor.getTenantId(jwt),
            request.varKey(), request.varValue(), request.varType(), request.description());
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
        String callerTenant = jwt.getClaimAsString("tenant_id");
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
