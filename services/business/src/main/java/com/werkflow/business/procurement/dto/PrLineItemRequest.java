package com.werkflow.business.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrLineItemRequest {
    @NotNull(message = "Line number is required")
    private Integer lineNumber;

    @NotBlank(message = "Description is required")
    @Size(max = 500)
    private String description;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @Size(max = 50)
    private String unitOfMeasure;

    @PositiveOrZero(message = "Estimated unit price must be zero or positive")
    private BigDecimal estimatedUnitPrice;

    private Long budgetCategoryId;

    private Map<String, Object> specifications;
}
