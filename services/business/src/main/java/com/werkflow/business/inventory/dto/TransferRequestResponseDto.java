package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for TransferRequest response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestResponseDto {

    private Long id;

    private Long assetInstanceId;

    private String assetTag;

    private Long fromDeptId;

    private String fromDeptName;

    private Long fromUserId;

    private Long toDeptId;

    private String toDeptName;

    private Long toUserId;

    private String transferType;

    private String transferReason;

    private LocalDate expectedReturnDate;

    private Long initiatedByUserId;

    private LocalDateTime initiatedDate;

    private Long approvedByUserId;

    private LocalDateTime approvedDate;

    private LocalDateTime completedDate;

    private String status;

    private String processInstanceId;

    private String rejectionReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
