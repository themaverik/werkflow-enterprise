package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.AssetInstance.AssetCondition;
import com.werkflow.business.inventory.entity.AssetInstance.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInstanceRequest {

    @NotNull(message = "Asset definition ID is required")
    private Long assetDefinitionId;

    @NotBlank(message = "Asset tag is required")
    @Size(max = 100, message = "Asset tag must not exceed 100 characters")
    private String assetTag;

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    private LocalDate purchaseDate;

    @Positive(message = "Purchase cost must be positive")
    private BigDecimal purchaseCost;

    private LocalDate warrantyExpiryDate;

    private AssetCondition condition;

    private AssetStatus status;

    @Size(max = 200, message = "Current location must not exceed 200 characters")
    private String currentLocation;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    private Map<String, Object> metadata;
}
