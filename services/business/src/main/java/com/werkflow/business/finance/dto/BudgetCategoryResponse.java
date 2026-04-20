package com.werkflow.business.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCategoryResponse {
    private Long id;
    private Long parentCategoryId;
    private String parentCategoryName;
    private String name;
    private String code;
    private String description;
    private Boolean active;
    private List<BudgetCategoryResponse> childCategories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
