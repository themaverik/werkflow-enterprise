package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.ReceiptLineItem.ItemCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptLineItemResponse {
    private Long id;
    private Long receiptId;
    private Long poLineItemId;
    private String itemDescription;
    private Integer receivedQuantity;
    private Integer acceptedQuantity;
    private Integer rejectedQuantity;
    private ItemCondition condition;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
