package com.werkflow.business.inventory.dto;

import com.werkflow.business.inventory.entity.TransferRequest.TransferStatus;
import com.werkflow.business.inventory.entity.TransferRequest.TransferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestRequest {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotNull(message = "From department ID is required")
    private Long fromDeptId;

    private Long fromUserId;

    @NotNull(message = "To department ID is required")
    private Long toDeptId;

    private Long toUserId;

    @NotNull(message = "Transfer type is required")
    private TransferType transferType;

    @NotBlank(message = "Transfer reason is required")
    @Size(max = 1000, message = "Transfer reason must not exceed 1000 characters")
    private String transferReason;

    private LocalDate expectedReturnDate;

    @NotNull(message = "Initiated by user ID is required")
    private Long initiatedByUserId;

    private Long approvedByUserId;

    private TransferStatus status;

    private String processInstanceId;

    @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
    private String rejectionReason;
}
