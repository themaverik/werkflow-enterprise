package com.werkflow.business.finance.dto;

import com.werkflow.business.finance.entity.Expense.ExpenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private Long budgetLineItemId;
    private Long departmentId;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private Long categoryId;
    private String categoryName;
    private String vendorName;
    private String description;
    private String receiptUrl;
    private ExpenseStatus status;
    private Long submittedByUserId;
    private Long approvedByUserId;
    private LocalDateTime approvedDate;
    private String processInstanceId;
    private String rejectionReason;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
