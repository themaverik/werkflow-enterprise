package com.werkflow.business.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO for AssetInstance creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInstanceRequestDto {

    @NotNull(message = "Asset definition ID is required")
    private Long assetDefinitionId;

    @NotBlank(message = "Asset tag is required")
    @Size(min = 3, max = 100, message = "Asset tag must be between 3 and 100 characters")
    private String assetTag;

    @Size(max = 100, message = "Serial number cannot exceed 100 characters")
    private String serialNumber;

    private LocalDate purchaseDate;

    @DecimalMin(value = "0.01", message = "Purchase cost must be greater than 0")
    private BigDecimal purchaseCost;

    private LocalDate warrantyExpiryDate;

    @Builder.Default
    private String condition = "NEW";

    @Builder.Default
    private String status = "AVAILABLE";

    @Size(max = 200, message = "Current location cannot exceed 200 characters")
    private String currentLocation;

    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;

    private Map<String, Object> metadata;
}
