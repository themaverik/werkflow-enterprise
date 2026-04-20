package com.werkflow.business.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrLineItemResponse {
    private Long id;
    private Long purchaseRequestId;
    private Integer lineNumber;
    private String description;
    private Integer quantity;
    private String unitOfMeasure;
    private BigDecimal estimatedUnitPrice;
    private BigDecimal totalPrice;
    private Long budgetCategoryId;
    private Map<String, Object> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
