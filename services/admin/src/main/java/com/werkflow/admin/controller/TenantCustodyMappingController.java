package com.werkflow.admin.controller;

import com.werkflow.admin.dto.CustodyMappingRequest;
import com.werkflow.admin.dto.CustodyMappingResponse;
import com.werkflow.admin.service.TenantCustodyMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/custody-mappings")
@RequiredArgsConstructor
@Tag(name = "Custody Mappings", description = "Manage tenant custody mappings (asset category to Flowable group)")
@SecurityRequirement(name = "bearer-jwt")
public class TenantCustodyMappingController {

    private final TenantCustodyMappingService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List custody mappings", description = "Get all custody mappings for a tenant")
    public ResponseEntity<List<CustodyMappingResponse>> list(@RequestParam String tenantCode) {
        return ResponseEntity.ok(service.listByTenant(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create custody mapping", description = "Create a new custody mapping")
    public ResponseEntity<CustodyMappingResponse> create(@Valid @RequestBody CustodyMappingRequest request) {
        CustodyMappingResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update custody mapping", description = "Update an existing custody mapping")
    public ResponseEntity<CustodyMappingResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody CustodyMappingRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Delete custody mapping", description = "Delete a custody mapping")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
