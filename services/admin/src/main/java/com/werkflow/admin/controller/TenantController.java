package com.werkflow.admin.controller;

import com.werkflow.admin.dto.EngineSeedResult;
import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.dto.TenantResponse;
import com.werkflow.admin.dto.TenantUpdateRequest;
import com.werkflow.admin.repository.TenantRepository;
import com.werkflow.admin.service.ExampleSeedClient;
import com.werkflow.admin.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Platform management endpoints for tenant lifecycle (ADR-030).
 * All endpoints are restricted to SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantRepository tenantRepository;
    private final ExampleSeedClient exampleSeedClient;

    /**
     * Lists all tenants in the platform.
     *
     * @return list of tenant summaries
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<TenantResponse>> listTenants() {
        List<TenantResponse> tenants = tenantRepository.findAll()
                .stream()
                .map(TenantResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tenants);
    }

    /**
     * Provisions a new tenant and its initial Keycloak admin user.
     *
     * @param request the provisioning request (validated)
     * @return the created tenant summary
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantProvisioningRequest request) {
        TenantResponse response = tenantProvisioningService.provision(request);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Updates a tenant's name and active status.
     *
     * @param id      the tenant ID
     * @param request the update payload (validated)
     * @return the updated tenant summary, or 404 if not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantUpdateRequest request) {
        return tenantRepository.findById(id)
                .map(tenant -> {
                    tenant.setName(request.getName());
                    tenant.setActive(request.isActive());
                    return ResponseEntity.ok(TenantResponse.from(tenantRepository.save(tenant)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Hard-deletes a tenant by ID.
     *
     * <p>Guards:
     * <ol>
     *   <li>The caller's own tenant (from JWT {@code tenant_id} claim) cannot be deleted.</li>
     *   <li>Tenants with active process instances cannot be deleted — the caller must complete
     *       or cancel all running instances first.</li>
     * </ol>
     *
     * @param id             the tenant ID
     * @param authentication the caller's JWT authentication (injected by Spring Security)
     * @return 204 No Content, 404 if not found, or 409 Conflict if a guard fires
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenant(
            @PathVariable Long id,
            Authentication authentication) {
        var tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }

        // Guard (a): prevent deleting the tenant the caller is currently logged in with.
        // callerTenantId is null for platform-level super_admins with no tenant scope;
        // in that case the guard passes (null cannot match any tenantCode).
        String callerTenantId = extractTenantId(authentication);
        if (callerTenantId != null && callerTenantId.equals(tenant.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete the tenant you are currently logged in with");
        }

        // Guard (b): prevent deleting a tenant with active process instances
        if (exampleSeedClient.hasActiveProcessInstances(tenant.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete tenant with active process instances — complete or cancel all running processes first");
        }

        tenantRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("tenant_id");
        }
        return null;
    }

    /**
     * Triggers example workflow seeding for an existing tenant via the engine service.
     *
     * @param id the tenant ID
     * @return the seed result, 503 if the engine is unavailable, or 404 if tenant not found
     */
    @PostMapping("/{id}/seed-examples")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<EngineSeedResult> seedExamples(@PathVariable Long id) {
        return tenantRepository.findById(id)
            .map(tenant -> exampleSeedClient.seed(tenant.getTenantCode())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).build()))
            .orElse(ResponseEntity.notFound().build());
    }
}
