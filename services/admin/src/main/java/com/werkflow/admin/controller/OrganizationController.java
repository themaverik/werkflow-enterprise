package com.werkflow.admin.controller;

import com.werkflow.admin.dto.OrganizationRequest;
import com.werkflow.admin.dto.OrganizationResponse;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management APIs")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create organization", description = "Create a new organization (SUPER_ADMIN only)")
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody OrganizationRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID", description = "Retrieve organization details by ID")
    public ResponseEntity<OrganizationResponse> getOrganizationById(@PathVariable Long id) {
        OrganizationResponse response = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all organizations", description = "Retrieve all organizations")
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations() {
        List<OrganizationResponse> response = organizationService.getAllOrganizations();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active organizations", description = "Retrieve all active organizations")
    public ResponseEntity<List<OrganizationResponse>> getActiveOrganizations() {
        List<OrganizationResponse> response = organizationService.getActiveOrganizations();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-tenant/{tenantCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get organization by tenant code")
    public ResponseEntity<OrganizationResponse> getByTenantCode(
            @PathVariable String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        String callerTenant = jwtClaimsExtractor.getTenantId(jwt);
        if (!jwtClaimsExtractor.hasRole(jwt, "SUPER_ADMIN") && !callerTenant.equals(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        OrganizationResponse response = organizationService.getByTenantCode(tenantCode);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update organization", description = "Update an existing organization (SUPER_ADMIN only)")
    public ResponseEntity<OrganizationResponse> updateOrganization(
        @PathVariable Long id,
        @Valid @RequestBody OrganizationRequest request
    ) {
        OrganizationResponse response = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete organization", description = "Delete an organization (SUPER_ADMIN only)")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}
