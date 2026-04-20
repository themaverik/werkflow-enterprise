package com.werkflow.business.inventory.service;

import com.werkflow.business.hr.entity.OfficeLocation;
import com.werkflow.business.inventory.dto.StockAvailabilityResponse;
import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.ItemType;
import com.werkflow.business.inventory.repository.AssetDefinitionRepository;
import com.werkflow.business.inventory.repository.AssetInstanceRepository;
import com.werkflow.business.inventory.repository.InventoryStockRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final AssetDefinitionRepository assetDefinitionRepository;
    private final AssetInstanceRepository assetInstanceRepository;
    private final InventoryStockRepository inventoryStockRepository;

    public StockAvailabilityResponse checkAvailability(Long assetDefinitionId, Integer quantity,
                                                        OfficeLocation officeLocation) {
        AssetDefinition def = assetDefinitionRepository.findById(assetDefinitionId)
            .orElseThrow(() -> new EntityNotFoundException("Asset definition not found: " + assetDefinitionId));

        if (def.getItemType() == ItemType.BULK) {
            Integer available = inventoryStockRepository.sumAvailableByDefinition(assetDefinitionId);
            available = available != null ? available : 0;
            return StockAvailabilityResponse.builder()
                .assetDefinitionId(assetDefinitionId)
                .assetName(def.getName())
                .sku(def.getSku())
                .itemType("BULK")
                .totalAvailable(available)
                .inStock(available >= quantity)
                .build();
        } else {
            long available = assetInstanceRepository.countByAssetDefinitionIdAndStatus(
                assetDefinitionId, "AVAILABLE");
            return StockAvailabilityResponse.builder()
                .assetDefinitionId(assetDefinitionId)
                .assetName(def.getName())
                .sku(def.getSku())
                .itemType("INDIVIDUAL")
                .totalAvailable((int) available)
                .inStock(available >= quantity)
                .build();
        }
    }
}
