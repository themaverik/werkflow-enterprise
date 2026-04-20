package com.werkflow.business.finance.dto;

import com.werkflow.business.finance.entity.BudgetPlan.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlanResponse {
    private Long id;
    private Long departmentId;
    private Integer fiscalYear;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalAmount;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BudgetStatus status;
    private Long createdByUserId;
    private Long approvedByUserId;
    private LocalDateTime approvedDate;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
