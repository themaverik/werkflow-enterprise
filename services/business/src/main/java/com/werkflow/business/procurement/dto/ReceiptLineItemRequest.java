package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.ReceiptLineItem.ItemCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptLineItemRequest {
    @NotNull(message = "PO line item ID is required")
    private Long poLineItemId;

    @NotNull(message = "Received quantity is required")
    @PositiveOrZero(message = "Received quantity must be zero or positive")
    private Integer receivedQuantity;

    @NotNull(message = "Accepted quantity is required")
    @PositiveOrZero(message = "Accepted quantity must be zero or positive")
    private Integer acceptedQuantity;

    @PositiveOrZero(message = "Rejected quantity must be zero or positive")
    private Integer rejectedQuantity;

    private ItemCondition condition;

    @Size(max = 1000)
    private String notes;
}
