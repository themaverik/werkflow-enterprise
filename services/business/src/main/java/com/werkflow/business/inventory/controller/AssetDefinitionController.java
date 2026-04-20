package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.AssetDefinitionRequestDto;
import com.werkflow.business.inventory.dto.AssetDefinitionResponseDto;
import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.ItemType;
import com.werkflow.business.inventory.service.AssetCategoryService;
import com.werkflow.business.inventory.service.AssetDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for AssetDefinition operations
 */
@RestController
@RequestMapping("/asset-definitions")
@RequiredArgsConstructor
@Tag(name = "Asset Definitions", description = "Asset definition management APIs")
public class AssetDefinitionController {

    private final AssetDefinitionService definitionService;
    private final AssetCategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create asset definition", description = "Create a new asset definition (template)")
    public ResponseEntity<AssetDefinitionResponseDto> createDefinition(@Valid @RequestBody AssetDefinitionRequestDto requestDto) {
        AssetCategory category = categoryService.getCategoryById(requestDto.getCategoryId());

        AssetDefinition definition = AssetDefinition.builder()
            .category(category)
            .sku(requestDto.getSku())
            .name(requestDto.getName())
            .manufacturer(requestDto.getManufacturer())
            .model(requestDto.getModel())
            .itemType(requestDto.getItemType() != null ? requestDto.getItemType() : ItemType.INDIVIDUAL)
            .specifications(requestDto.getSpecifications())
            .unitCost(requestDto.getUnitCost())
            .expectedLifespanMonths(requestDto.getExpectedLifespanMonths())
            .requiresMaintenance(requestDto.getRequiresMaintenance())
            .maintenanceIntervalMonths(requestDto.getMaintenanceIntervalMonths())
            .active(requestDto.getActive())
            .build();

        AssetDefinition created = definitionService.createDefinition(definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset definition by ID", description = "Retrieve an asset definition by its ID")
    public ResponseEntity<AssetDefinitionResponseDto> getDefinitionById(@PathVariable Long id) {
        AssetDefinition definition = definitionService.getDefinitionById(id);
        return ResponseEntity.ok(mapToResponse(definition));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get asset definition by SKU", description = "Retrieve an asset definition by its SKU")
    public ResponseEntity<AssetDefinitionResponseDto> getDefinitionBySku(@PathVariable String sku) {
        AssetDefinition definition = definitionService.getDefinitionBySku(sku);
        return ResponseEntity.ok(mapToResponse(definition));
    }

    @GetMapping
    @Operation(summary = "Get asset definitions", description = "Retrieve asset definitions; pass categoryId to filter by category")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getAllDefinitions(
            @RequestParam(required = false) Long categoryId) {
        List<AssetDefinition> definitions = categoryId != null
            ? definitionService.getDefinitionsByCategory(categoryId)
            : definitionService.getAllDefinitions();
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active asset definitions", description = "Retrieve all active asset definitions")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getActiveDefinitions() {
        List<AssetDefinition> definitions = definitionService.getActiveDefinitions();
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get definitions by category", description = "Retrieve asset definitions for a specific category")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getDefinitionsByCategory(@PathVariable Long categoryId) {
        List<AssetDefinition> definitions = definitionService.getDefinitionsByCategory(categoryId);
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/maintenance")
    @Operation(summary = "Get definitions requiring maintenance", description = "Retrieve asset definitions that require periodic maintenance")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getDefinitionsRequiringMaintenance() {
        List<AssetDefinition> definitions = definitionService.getDefinitionsRequiringMaintenance();
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/manufacturer/{manufacturer}")
    @Operation(summary = "Get definitions by manufacturer", description = "Retrieve asset definitions from a specific manufacturer")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getDefinitionsByManufacturer(@PathVariable String manufacturer) {
        List<AssetDefinition> definitions = definitionService.getDefinitionsByManufacturer(manufacturer);
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/price-range")
    @Operation(summary = "Get definitions by price range", description = "Retrieve asset definitions within a price range")
    public ResponseEntity<List<AssetDefinitionResponseDto>> getDefinitionsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        List<AssetDefinition> definitions = definitionService.getDefinitionsByPriceRange(minPrice, maxPrice);
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search definitions", description = "Search asset definitions by name or SKU")
    public ResponseEntity<List<AssetDefinitionResponseDto>> searchDefinitions(@RequestParam String searchTerm) {
        List<AssetDefinition> definitions = definitionService.searchDefinitions(searchTerm);
        return ResponseEntity.ok(definitions.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update asset definition", description = "Update an existing asset definition")
    public ResponseEntity<AssetDefinitionResponseDto> updateDefinition(
            @PathVariable Long id,
            @Valid @RequestBody AssetDefinitionRequestDto requestDto) {
        AssetCategory category = categoryService.getCategoryById(requestDto.getCategoryId());

        AssetDefinition definitionDetails = AssetDefinition.builder()
            .category(category)
            .sku(requestDto.getSku())
            .name(requestDto.getName())
            .manufacturer(requestDto.getManufacturer())
            .model(requestDto.getModel())
            .itemType(requestDto.getItemType() != null ? requestDto.getItemType() : ItemType.INDIVIDUAL)
            .specifications(requestDto.getSpecifications())
            .unitCost(requestDto.getUnitCost())
            .expectedLifespanMonths(requestDto.getExpectedLifespanMonths())
            .requiresMaintenance(requestDto.getRequiresMaintenance())
            .maintenanceIntervalMonths(requestDto.getMaintenanceIntervalMonths())
            .active(requestDto.getActive())
            .build();

        AssetDefinition updated = definitionService.updateDefinition(id, definitionDetails);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate asset definition", description = "Deactivate an asset definition")
    public ResponseEntity<AssetDefinitionResponseDto> deactivateDefinition(@PathVariable Long id) {
        AssetDefinition deactivated = definitionService.deactivateDefinition(id);
        return ResponseEntity.ok(mapToResponse(deactivated));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate asset definition", description = "Activate an asset definition")
    public ResponseEntity<AssetDefinitionResponseDto> activateDefinition(@PathVariable Long id) {
        AssetDefinition activated = definitionService.activateDefinition(id);
        return ResponseEntity.ok(mapToResponse(activated));
    }

    private AssetDefinitionResponseDto mapToResponse(AssetDefinition definition) {
        return AssetDefinitionResponseDto.builder()
            .id(definition.getId())
            .categoryId(definition.getCategory().getId())
            .categoryName(definition.getCategory().getName())
            .sku(definition.getSku())
            .name(definition.getName())
            .manufacturer(definition.getManufacturer())
            .model(definition.getModel())
            .itemType(definition.getItemType() != null ? definition.getItemType().name() : ItemType.INDIVIDUAL.name())
            .specifications(definition.getSpecifications())
            .unitCost(definition.getUnitCost())
            .expectedLifespanMonths(definition.getExpectedLifespanMonths())
            .requiresMaintenance(definition.getRequiresMaintenance())
            .maintenanceIntervalMonths(definition.getMaintenanceIntervalMonths())
            .active(definition.getActive())
            .createdAt(definition.getCreatedAt())
            .updatedAt(definition.getUpdatedAt())
            .build();
    }
}
