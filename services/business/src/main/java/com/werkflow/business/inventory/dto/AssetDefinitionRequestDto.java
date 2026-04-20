package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.ItemType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for AssetDefinition creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDefinitionRequestDto {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 100, message = "SKU must be between 3 and 100 characters")
    private String sku;

    @NotBlank(message = "Asset name is required")
    @Size(min = 5, max = 200, message = "Asset name must be between 5 and 200 characters")
    private String name;

    @Size(max = 100, message = "Manufacturer cannot exceed 100 characters")
    private String manufacturer;

    @Size(max = 100, message = "Model cannot exceed 100 characters")
    private String model;

    @Builder.Default
    private ItemType itemType = ItemType.INDIVIDUAL;

    private Map<String, Object> specifications;

    @DecimalMin(value = "0.01", message = "Unit cost must be greater than 0")
    private BigDecimal unitCost;

    private Integer expectedLifespanMonths;

    @Builder.Default
    private Boolean requiresMaintenance = false;

    private Integer maintenanceIntervalMonths;

    @Builder.Default
    private Boolean active = true;
}
