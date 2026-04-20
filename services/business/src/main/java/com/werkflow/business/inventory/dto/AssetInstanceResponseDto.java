package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for AssetInstance response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInstanceResponseDto {

    private Long id;

    private Long assetDefinitionId;

    private String assetDefinitionName;

    private String assetTag;

    private String serialNumber;

    private LocalDate purchaseDate;

    private BigDecimal purchaseCost;

    private LocalDate warrantyExpiryDate;

    private String condition;

    private String status;

    private String currentLocation;

    private String notes;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
