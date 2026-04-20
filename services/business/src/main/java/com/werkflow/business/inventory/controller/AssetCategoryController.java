package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.AssetCategoryRequestDto;
import com.werkflow.business.inventory.dto.AssetCategoryResponseDto;
import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.service.AssetCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for AssetCategory operations
 */
@RestController
@RequestMapping("/asset-categories")
@RequiredArgsConstructor
@Tag(name = "Asset Categories", description = "Asset category management APIs")
public class AssetCategoryController {

    private final AssetCategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create asset category", description = "Create a new asset category")
    public ResponseEntity<AssetCategoryResponseDto> createCategory(@Valid @RequestBody AssetCategoryRequestDto requestDto) {
        AssetCategory category = AssetCategory.builder()
            .name(requestDto.getName())
            .code(requestDto.getCode())
            .description(requestDto.getDescription())
            .requiresApproval(requestDto.getRequiresApproval())
            .active(requestDto.getActive())
            .custodianDeptCode(requestDto.getCustodianDeptCode())
            .custodianUserId(requestDto.getCustodianUserId())
            .build();

        if (requestDto.getParentCategoryId() != null) {
            AssetCategory parent = categoryService.getCategoryById(requestDto.getParentCategoryId());
            category.setParentCategory(parent);
        }

        AssetCategory created = categoryService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset category by ID", description = "Retrieve an asset category by its ID")
    public ResponseEntity<AssetCategoryResponseDto> getCategoryById(@PathVariable Long id) {
        AssetCategory category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(mapToResponse(category));
    }

    @GetMapping
    @Operation(summary = "Get asset categories", description = "Retrieve asset categories; pass parentCategoryId to filter by parent, or leafOnly=true for all subcategories")
    public ResponseEntity<List<AssetCategoryResponseDto>> getAllCategories(
            @RequestParam(required = false) Long parentCategoryId,
            @RequestParam(required = false) Boolean leafOnly) {
        List<AssetCategory> categories;
        if (parentCategoryId != null) {
            categories = categoryService.getChildCategories(parentCategoryId);
        } else if (Boolean.TRUE.equals(leafOnly)) {
            categories = categoryService.getActiveSubcategories();
        } else {
            categories = categoryService.getAllCategories();
        }
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active asset categories", description = "Retrieve all active asset categories")
    public ResponseEntity<List<AssetCategoryResponseDto>> getActiveCategories() {
        List<AssetCategory> categories = categoryService.getActiveCategories();
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/root")
    @Operation(summary = "Get root asset categories", description = "Retrieve root categories (no parent)")
    public ResponseEntity<List<AssetCategoryResponseDto>> getRootCategories() {
        List<AssetCategory> categories = categoryService.getRootCategories();
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{parentId}/children")
    @Operation(summary = "Get child categories", description = "Retrieve child categories of a parent")
    public ResponseEntity<List<AssetCategoryResponseDto>> getChildCategories(@PathVariable Long parentId) {
        List<AssetCategory> categories = categoryService.getChildCategories(parentId);
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search categories", description = "Search asset categories by name or code")
    public ResponseEntity<List<AssetCategoryResponseDto>> searchCategories(@RequestParam String searchTerm) {
        List<AssetCategory> categories = categoryService.searchCategories(searchTerm);
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update asset category", description = "Update an existing asset category")
    public ResponseEntity<AssetCategoryResponseDto> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody AssetCategoryRequestDto requestDto) {
        AssetCategory categoryDetails = AssetCategory.builder()
            .name(requestDto.getName())
            .code(requestDto.getCode())
            .description(requestDto.getDescription())
            .requiresApproval(requestDto.getRequiresApproval())
            .active(requestDto.getActive())
            .custodianDeptCode(requestDto.getCustodianDeptCode())
            .custodianUserId(requestDto.getCustodianUserId())
            .build();

        AssetCategory updated = categoryService.updateCategory(id, categoryDetails);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate asset category", description = "Deactivate an asset category")
    public ResponseEntity<AssetCategoryResponseDto> deactivateCategory(@PathVariable Long id) {
        AssetCategory deactivated = categoryService.deactivateCategory(id);
        return ResponseEntity.ok(mapToResponse(deactivated));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate asset category", description = "Activate an asset category")
    public ResponseEntity<AssetCategoryResponseDto> activateCategory(@PathVariable Long id) {
        AssetCategory activated = categoryService.activateCategory(id);
        return ResponseEntity.ok(mapToResponse(activated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete asset category", description = "Delete an asset category")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tree")
    @Operation(summary = "Get category tree", description = "Retrieve full category tree from roots")
    public ResponseEntity<List<AssetCategoryResponseDto>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    private AssetCategoryResponseDto mapToResponse(AssetCategory category) {
        return AssetCategoryResponseDto.builder()
            .id(category.getId())
            .name(category.getName())
            .code(category.getCode())
            .description(category.getDescription())
            .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
            .custodianDeptCode(category.getCustodianDeptCode())
            .custodianUserId(category.getCustodianUserId())
            .requiresApproval(category.getRequiresApproval())
            .active(category.getActive())
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .build();
    }
}
