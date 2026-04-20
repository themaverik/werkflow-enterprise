package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.CustodyRecord.CustodyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyRecordRequest {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotNull(message = "Custodian department ID is required")
    private Long custodianDeptId;

    private Long custodianUserId;

    @Size(max = 200, message = "Physical location must not exceed 200 characters")
    private String physicalLocation;

    @NotNull(message = "Custody type is required")
    private CustodyType custodyType;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Long assignedByUserId;

    @Size(max = 50, message = "Return condition must not exceed 50 characters")
    private String returnCondition;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}
