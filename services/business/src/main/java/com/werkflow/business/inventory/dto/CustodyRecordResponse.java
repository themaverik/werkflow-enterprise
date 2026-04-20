package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.CustodyRecord.CustodyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyRecordResponse {

    private Long id;
    private Long assetInstanceId;
    private String assetTag;
    private Long custodianDeptId;
    private Long custodianUserId;
    private String physicalLocation;
    private CustodyType custodyType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long assignedByUserId;
    private String returnCondition;
    private String notes;
    private LocalDateTime createdAt;
}
