package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.AssetInstance.AssetCondition;
import com.werkflow.business.inventory.entity.AssetInstance.AssetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInstanceResponse {

    private Long id;
    private Long assetDefinitionId;
    private String assetDefinitionName;
    private String assetTag;
    private String serialNumber;
    private LocalDate purchaseDate;
    private BigDecimal purchaseCost;
    private LocalDate warrantyExpiryDate;
    private AssetCondition condition;
    private AssetStatus status;
    private String currentLocation;
    private String notes;
    private Map<String, Object> metadata;
    private CustodyRecordResponse currentCustody;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
