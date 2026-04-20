package com.werkflow.business.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCategoryRequest {
    private Long parentCategoryId;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String code;

    @Size(max = 1000)
    private String description;

    private Boolean active;
}
