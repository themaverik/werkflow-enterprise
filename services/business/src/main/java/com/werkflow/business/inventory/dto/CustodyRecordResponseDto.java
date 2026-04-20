package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for CustodyRecord response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyRecordResponseDto {

    private Long id;

    private Long assetInstanceId;

    private String assetTag;

    private Long custodianDeptId;

    private String custodianDeptName;

    private Long custodianUserId;

    private String physicalLocation;

    private String custodyType;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Long assignedByUserId;

    private String returnCondition;

    private String notes;

    private LocalDateTime createdAt;

    private Boolean isActive;
}
