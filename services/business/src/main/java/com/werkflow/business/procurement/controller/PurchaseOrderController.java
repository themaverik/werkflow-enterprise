package com.werkflow.business.procurement.controller;

import com.werkflow.business.procurement.dto.PurchaseOrderRequest;
import com.werkflow.business.procurement.dto.PurchaseOrderResponse;
import com.werkflow.business.procurement.service.PurchaseOrderService;
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
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Purchase order management endpoints")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @GetMapping
    @Operation(summary = "Get all purchase orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PurchaseOrderResponse>> getAllPurchaseOrders() {
        return ResponseEntity.ok(poService.getAllPurchaseOrders());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get purchase order by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(poService.getPurchaseOrderById(id));
    }

    @PostMapping
    @Operation(summary = "Create new purchase order")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(poService.createPurchaseOrder(request));
    }
}
