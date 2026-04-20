package com.werkflow.business.inventory.dto;

import com.werkflow.business.hr.entity.OfficeLocation;
import com.werkflow.business.inventory.entity.AssetRequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRequestResponse {
    private Long id;
    private String processInstanceId;
    private String requesterUserId;
    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;
    private String departmentCode;
    private OfficeLocation officeLocation;
    private Long assetDefinitionId;
    private String assetName;
    private Long assetCategoryId;
    private String assetCategoryName;
    private Integer quantity;
    private Boolean procurementRequired;
    private BigDecimal approxPrice;
    private LocalDate deliveryDate;
    private String justification;
    private AssetRequestStatus status;
    private String approvedByUserId;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
