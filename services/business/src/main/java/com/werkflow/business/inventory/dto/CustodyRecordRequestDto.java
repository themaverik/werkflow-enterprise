package com.werkflow.business.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for CustodyRecord creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyRecordRequestDto {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotNull(message = "Custodian department ID is required")
    private Long custodianDeptId;

    private Long custodianUserId;

    @Size(max = 200, message = "Physical location cannot exceed 200 characters")
    private String physicalLocation;

    @NotBlank(message = "Custody type is required")
    private String custodyType;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Long assignedByUserId;

    private String returnCondition;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}
