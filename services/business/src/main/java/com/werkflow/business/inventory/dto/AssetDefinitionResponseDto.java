package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for AssetDefinition response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDefinitionResponseDto {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String sku;

    private String name;

    private String manufacturer;

    private String model;

    private String itemType;

    private Map<String, Object> specifications;

    private BigDecimal unitCost;

    private Integer expectedLifespanMonths;

    private Boolean requiresMaintenance;

    private Integer maintenanceIntervalMonths;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
