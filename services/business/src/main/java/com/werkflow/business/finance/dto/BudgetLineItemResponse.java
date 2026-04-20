package com.werkflow.business.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetLineItemResponse {
    private Long id;
    private Long budgetPlanId;
    private Long categoryId;
    private String categoryName;
    private String description;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
