package com.werkflow.admin.controller;

import com.werkflow.admin.dto.TenantProvisioningRequest;
import com.werkflow.admin.dto.TenantResponse;
import com.werkflow.admin.dto.TenantUpdateRequest;
import com.werkflow.admin.repository.TenantRepository;
import com.werkflow.admin.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
     * @param id the tenant ID
     * @return 204 No Content, or 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        if (!tenantRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tenantRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
