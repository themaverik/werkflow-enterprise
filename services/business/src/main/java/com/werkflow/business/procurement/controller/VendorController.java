package com.werkflow.business.procurement.controller;

import com.werkflow.business.procurement.dto.VendorRequest;
import com.werkflow.business.procurement.dto.VendorResponse;
import com.werkflow.business.procurement.service.VendorService;
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
@RequestMapping("/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendors", description = "Vendor management endpoints")
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    @Operation(summary = "Get all vendors")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<VendorResponse>> getAllVendors() {
        return ResponseEntity.ok(vendorService.getAllVendors());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vendor by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VendorResponse> getVendorById(@PathVariable Long id) {
        return ResponseEntity.ok(vendorService.getVendorById(id));
    }

    @PostMapping
    @Operation(summary = "Create new vendor")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VendorResponse> createVendor(@Valid @RequestBody VendorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vendorService.createVendor(request));
    }
}
