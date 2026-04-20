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
public class ApprovalThresholdResponse {
    private Long id;
    private Long departmentId;
    private Long categoryId;
    private String categoryName;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String requiredRole;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
