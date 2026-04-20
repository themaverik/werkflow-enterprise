package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.PurchaseRequest.Priority;
import com.werkflow.business.procurement.entity.PurchaseRequest.PrStatus;
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
public class PurchaseRequestResponse {
    private Long id;
    private String prNumber;
    private Long requestingDeptId;
    private Long requesterUserId;
    private LocalDate requestDate;
    private LocalDate requiredByDate;
    private Priority priority;
    private BigDecimal totalAmount;
    private PrStatus status;
    private Long approvedByUserId;
    private LocalDateTime approvedDate;
    private String processInstanceId;
    private String rejectionReason;
    private String justification;
    private String notes;
    private List<PrLineItemResponse> lineItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
