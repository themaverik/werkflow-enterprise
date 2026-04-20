package com.werkflow.business.finance.controller;

import com.werkflow.business.finance.dto.BudgetLineItemRequest;
import com.werkflow.business.finance.dto.BudgetLineItemResponse;
import com.werkflow.business.finance.service.BudgetLineItemService;
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
@RequestMapping("/budget-line-items")
@RequiredArgsConstructor
@Tag(name = "Budget Line Items", description = "Budget line item management endpoints")
public class BudgetLineItemController {

    private final BudgetLineItemService lineItemService;

    @GetMapping
    @Operation(summary = "Get line items by budget plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BudgetLineItemResponse>> getLineItemsByBudgetPlan(@RequestParam Long budgetPlanId) {
        return ResponseEntity.ok(lineItemService.getLineItemsByBudgetPlan(budgetPlanId));
    }

    @PostMapping
    @Operation(summary = "Create new budget line item")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<BudgetLineItemResponse> createLineItem(@Valid @RequestBody BudgetLineItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lineItemService.createLineItem(request));
    }
}
