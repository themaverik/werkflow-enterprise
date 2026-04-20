package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.Receipt.ReceiptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {
    private Long id;
    private String receiptNumber;
    private Long purchaseOrderId;
    private String poNumber;
    private LocalDate receiptDate;
    private Long receivedByUserId;
    private ReceiptStatus status;
    private String notes;
    private String discrepancyNotes;
    private List<ReceiptLineItemResponse> lineItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
