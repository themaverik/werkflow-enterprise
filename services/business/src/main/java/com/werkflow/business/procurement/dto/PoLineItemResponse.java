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
public class PoLineItemResponse {
    private Long id;
    private Long purchaseOrderId;
    private Long prLineItemId;
    private Integer lineNumber;
    private String description;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private String unitOfMeasure;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Map<String, Object> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
