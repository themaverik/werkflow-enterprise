package com.werkflow.business.finance.controller;

import com.werkflow.business.finance.dto.BudgetCategoryRequest;
import com.werkflow.business.finance.dto.BudgetCategoryResponse;
import com.werkflow.business.finance.service.BudgetCategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Budget Categories", description = "Budget category management endpoints")
public class BudgetCategoryController {

    private final BudgetCategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all budget categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BudgetCategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get budget category by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BudgetCategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    @Operation(summary = "Create new budget category")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<BudgetCategoryResponse> createCategory(@Valid @RequestBody BudgetCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update budget category")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<BudgetCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody BudgetCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete budget category")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
