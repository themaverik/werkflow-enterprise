package com.werkflow.business.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for budget availability check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCheckResponse {

    private boolean available;

    private String reason;

    private BigDecimal requestedAmount;

    private BigDecimal availableAmount;

    private BigDecimal allocatedAmount;

    private BigDecimal utilizedAmount;

    private String costCenter;

    private Integer fiscalYear;
}
