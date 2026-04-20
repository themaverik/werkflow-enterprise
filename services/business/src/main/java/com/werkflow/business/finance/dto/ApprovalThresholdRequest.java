package com.werkflow.business.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalThresholdRequest {
    private Long departmentId;

    private Long categoryId;

    @NotNull(message = "Minimum amount is required")
    @PositiveOrZero(message = "Minimum amount must be zero or positive")
    private BigDecimal minAmount;

    @PositiveOrZero(message = "Maximum amount must be zero or positive")
    private BigDecimal maxAmount;

    @NotBlank(message = "Required role is required")
    @Size(max = 50)
    private String requiredRole;

    @Size(max = 500)
    private String description;

    private Boolean active;
}
