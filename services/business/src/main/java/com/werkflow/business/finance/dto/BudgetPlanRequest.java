package com.werkflow.business.finance.dto;

import com.werkflow.business.finance.entity.BudgetPlan.BudgetStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlanRequest {
    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotNull(message = "Fiscal year is required")
    private Integer fiscalYear;

    @NotNull(message = "Period start is required")
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    private LocalDate periodEnd;

    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private BigDecimal totalAmount;

    private BudgetStatus status;

    @NotNull(message = "Created by user ID is required")
    private Long createdByUserId;

    @Size(max = 2000)
    private String notes;
}
