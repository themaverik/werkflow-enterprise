package com.werkflow.business.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for budget availability check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCheckRequest {

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String costCenter;

    private Integer fiscalYear;
}
