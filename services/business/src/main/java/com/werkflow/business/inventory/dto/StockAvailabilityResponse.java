package com.werkflow.business.inventory.dto;

import com.werkflow.business.hr.entity.OfficeLocation;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAvailabilityResponse {
    private Long assetDefinitionId;
    private String assetName;
    private String sku;
    private String itemType;
    private Integer totalAvailable;
    private Boolean inStock;
    private OfficeLocation officeLocation;
    private String stockLocationName;
}
