package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.PurchaseOrder.PoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {
    private Long id;
    private String poNumber;
    private Long purchaseRequestId;
    private Long vendorId;
    private String vendorName;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private BigDecimal totalAmount;
    private PoStatus status;
    private Long createdByUserId;
    private String deliveryAddress;
    private String paymentTerms;
    private String notes;
    private List<PoLineItemResponse> lineItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
