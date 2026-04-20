package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDefinitionRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 100, message = "Manufacturer must not exceed 100 characters")
    private String manufacturer;

    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    private ItemType itemType;

    private Map<String, Object> specifications;

    @Positive(message = "Unit cost must be positive")
    private BigDecimal unitCost;

    @Positive(message = "Expected lifespan must be positive")
    private Integer expectedLifespanMonths;

    private Boolean requiresMaintenance;

    @Positive(message = "Maintenance interval must be positive")
    private Integer maintenanceIntervalMonths;

    private Boolean active;
}
