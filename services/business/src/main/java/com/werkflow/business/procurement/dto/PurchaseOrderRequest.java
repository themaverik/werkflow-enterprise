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
public class PurchaseOrderRequest {
    private Long purchaseRequestId;

    @NotNull(message = "Vendor ID is required")
    private Long vendorId;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    @NotNull(message = "Created by user ID is required")
    private Long createdByUserId;

    @Size(max = 500)
    private String deliveryAddress;

    @Size(max = 100)
    private String paymentTerms;

    @Size(max = 1000)
    private String notes;

    @Valid
    private List<PoLineItemRequest> lineItems;
}
