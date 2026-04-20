package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.TransferRequest.TransferStatus;
import com.werkflow.business.inventory.entity.TransferRequest.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestResponse {

    private Long id;
    private Long assetInstanceId;
    private String assetTag;
    private String assetName;
    private Long fromDeptId;
    private Long fromUserId;
    private Long toDeptId;
    private Long toUserId;
    private TransferType transferType;
    private String transferReason;
    private LocalDate expectedReturnDate;
    private Long initiatedByUserId;
    private LocalDateTime initiatedDate;
    private Long approvedByUserId;
    private LocalDateTime approvedDate;
    private LocalDateTime completedDate;
    private TransferStatus status;
    private String processInstanceId;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
