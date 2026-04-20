package com.werkflow.business.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptRequest {
    @NotNull(message = "Purchase order ID is required")
    private Long purchaseOrderId;

    @NotNull(message = "Receipt date is required")
    private LocalDate receiptDate;

    @NotNull(message = "Received by user ID is required")
    private Long receivedByUserId;

    @Size(max = 1000)
    private String notes;

    @Size(max = 2000)
    private String discrepancyNotes;

    @Valid
    private List<ReceiptLineItemRequest> lineItems;
}
