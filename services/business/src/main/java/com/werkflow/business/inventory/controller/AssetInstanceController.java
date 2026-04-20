package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.AssetInstanceRequestDto;
import com.werkflow.business.inventory.dto.AssetInstanceResponseDto;
import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.service.AssetDefinitionService;
import com.werkflow.business.inventory.service.AssetInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for AssetInstance operations
 */
@RestController
@RequestMapping("/asset-instances")
@RequiredArgsConstructor
@Tag(name = "Asset Instances", description = "Asset instance (physical asset) management APIs")
public class AssetInstanceController {

    private final AssetInstanceService instanceService;
    private final AssetDefinitionService definitionService;

    @PostMapping
    @Operation(summary = "Create asset instance", description = "Create a new physical asset instance")
    public ResponseEntity<AssetInstanceResponseDto> createInstance(@Valid @RequestBody AssetInstanceRequestDto requestDto) {
        AssetDefinition definition = definitionService.getDefinitionById(requestDto.getAssetDefinitionId());

        AssetInstance instance = AssetInstance.builder()
            .assetDefinition(definition)
            .assetTag(requestDto.getAssetTag())
            .serialNumber(requestDto.getSerialNumber())
            .purchaseDate(requestDto.getPurchaseDate())
            .purchaseCost(requestDto.getPurchaseCost())
            .warrantyExpiryDate(requestDto.getWarrantyExpiryDate())
            .condition(AssetInstance.AssetCondition.valueOf(requestDto.getCondition()))
            .status(AssetInstance.AssetStatus.valueOf(requestDto.getStatus()))
            .currentLocation(requestDto.getCurrentLocation())
            .notes(requestDto.getNotes())
            .metadata(requestDto.getMetadata())
            .build();

        AssetInstance created = instanceService.createInstance(instance);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset instance by ID", description = "Retrieve an asset instance by its ID")
    public ResponseEntity<AssetInstanceResponseDto> getInstanceById(@PathVariable Long id) {
        AssetInstance instance = instanceService.getInstanceById(id);
        return ResponseEntity.ok(mapToResponse(instance));
    }

    @GetMapping("/tag/{assetTag}")
    @Operation(summary = "Get asset instance by tag", description = "Retrieve an asset instance by its barcode/tag")
    public ResponseEntity<AssetInstanceResponseDto> getInstanceByAssetTag(@PathVariable String assetTag) {
        AssetInstance instance = instanceService.getInstanceByAssetTag(assetTag);
        return ResponseEntity.ok(mapToResponse(instance));
    }

    @GetMapping
    @Operation(summary = "Get all asset instances", description = "Retrieve all physical asset instances")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAllInstances() {
        List<AssetInstance> instances = instanceService.getAllInstances();
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/definition/{definitionId}")
    @Operation(summary = "Get instances by definition", description = "Retrieve all instances of a specific asset definition")
    public ResponseEntity<List<AssetInstanceResponseDto>> getInstancesByDefinition(@PathVariable Long definitionId) {
        List<AssetInstance> instances = instanceService.getInstancesByDefinition(definitionId);
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get instances by status", description = "Retrieve asset instances by status")
    public ResponseEntity<List<AssetInstanceResponseDto>> getInstancesByStatus(@PathVariable String status) {
        List<AssetInstance> instances = instanceService.getInstancesByStatus(status);
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/available")
    @Operation(summary = "Get available assets", description = "Retrieve all available (not in use) asset instances")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAvailableAssets() {
        List<AssetInstance> instances = instanceService.getAvailableAssets();
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/in-use")
    @Operation(summary = "Get assets in use", description = "Retrieve all assets currently in use")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAssetsInUse() {
        List<AssetInstance> instances = instanceService.getAssetsInUse();
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/maintenance")
    @Operation(summary = "Get assets requiring maintenance", description = "Retrieve assets currently in maintenance")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAssetsRequiringMaintenance() {
        List<AssetInstance> instances = instanceService.getAssetsRequiringMaintenance();
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/expiring-warranty")
    @Operation(summary = "Get assets with expiring warranty", description = "Retrieve assets with warranty expiring soon")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAssetsWithExpiringWarranty(
            @RequestParam(defaultValue = "30") Integer daysFromNow) {
        LocalDate expiryDate = LocalDate.now().plusDays(daysFromNow);
        List<AssetInstance> instances = instanceService.getAssetsWithExpiringWarranty(expiryDate);
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/attention")
    @Operation(summary = "Get assets needing attention", description = "Retrieve assets in poor/damaged condition")
    public ResponseEntity<List<AssetInstanceResponseDto>> getAssetsNeedingAttention() {
        List<AssetInstance> instances = instanceService.getAssetsNeedingAttention();
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search asset instances", description = "Search assets by tag or serial number")
    public ResponseEntity<List<AssetInstanceResponseDto>> searchInstances(@RequestParam String searchTerm) {
        List<AssetInstance> instances = instanceService.searchInstances(searchTerm);
        return ResponseEntity.ok(instances.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update asset instance", description = "Update an existing asset instance")
    public ResponseEntity<AssetInstanceResponseDto> updateInstance(
            @PathVariable Long id,
            @Valid @RequestBody AssetInstanceRequestDto requestDto) {
        AssetDefinition definition = definitionService.getDefinitionById(requestDto.getAssetDefinitionId());

        AssetInstance instanceDetails = AssetInstance.builder()
            .assetDefinition(definition)
            .assetTag(requestDto.getAssetTag())
            .serialNumber(requestDto.getSerialNumber())
            .purchaseDate(requestDto.getPurchaseDate())
            .purchaseCost(requestDto.getPurchaseCost())
            .warrantyExpiryDate(requestDto.getWarrantyExpiryDate())
            .condition(AssetInstance.AssetCondition.valueOf(requestDto.getCondition()))
            .status(AssetInstance.AssetStatus.valueOf(requestDto.getStatus()))
            .currentLocation(requestDto.getCurrentLocation())
            .notes(requestDto.getNotes())
            .metadata(requestDto.getMetadata())
            .build();

        AssetInstance updated = instanceService.updateInstance(id, instanceDetails);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update asset status", description = "Update the status of an asset instance")
    public ResponseEntity<AssetInstanceResponseDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        AssetInstance updated = instanceService.updateStatus(id, status);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    private AssetInstanceResponseDto mapToResponse(AssetInstance instance) {
        return AssetInstanceResponseDto.builder()
            .id(instance.getId())
            .assetDefinitionId(instance.getAssetDefinition().getId())
            .assetDefinitionName(instance.getAssetDefinition().getName())
            .assetTag(instance.getAssetTag())
            .serialNumber(instance.getSerialNumber())
            .purchaseDate(instance.getPurchaseDate())
            .purchaseCost(instance.getPurchaseCost())
            .warrantyExpiryDate(instance.getWarrantyExpiryDate())
            .condition(instance.getCondition().toString())
            .status(instance.getStatus().toString())
            .currentLocation(instance.getCurrentLocation())
            .notes(instance.getNotes())
            .metadata(instance.getMetadata())
            .createdAt(instance.getCreatedAt())
            .updatedAt(instance.getUpdatedAt())
            .build();
    }
}
