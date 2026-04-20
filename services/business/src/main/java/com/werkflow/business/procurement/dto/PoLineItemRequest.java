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
public class PoLineItemRequest {
    private Long prLineItemId;

    @NotNull(message = "Line number is required")
    private Integer lineNumber;

    @NotBlank(message = "Description is required")
    @Size(max = 500)
    private String description;

    @NotNull(message = "Ordered quantity is required")
    @Positive(message = "Ordered quantity must be positive")
    private Integer orderedQuantity;

    @Size(max = 50)
    private String unitOfMeasure;

    @NotNull(message = "Unit price is required")
    @PositiveOrZero(message = "Unit price must be zero or positive")
    private BigDecimal unitPrice;

    private Map<String, Object> specifications;
}
