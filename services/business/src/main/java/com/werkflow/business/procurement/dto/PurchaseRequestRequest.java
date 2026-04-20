package com.werkflow.business.procurement.dto;

import com.werkflow.business.procurement.entity.PurchaseRequest.Priority;
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
public class PurchaseRequestRequest {
    @NotNull(message = "Requesting department ID is required")
    private Long requestingDeptId;

    @NotNull(message = "Requester user ID is required")
    private Long requesterUserId;

    @NotNull(message = "Request date is required")
    private LocalDate requestDate;

    private LocalDate requiredByDate;

    private Priority priority;

    @Size(max = 2000)
    private String justification;

    @Size(max = 1000)
    private String notes;

    @Valid
    private List<PrLineItemRequest> lineItems;
}
