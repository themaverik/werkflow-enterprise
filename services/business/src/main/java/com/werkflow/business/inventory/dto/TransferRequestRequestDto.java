package com.werkflow.business.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for TransferRequest creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestRequestDto {

    @NotNull(message = "Asset instance ID is required")
    private Long assetInstanceId;

    @NotNull(message = "From department ID is required")
    private Long fromDeptId;

    private Long fromUserId;

    @NotNull(message = "To department ID is required")
    private Long toDeptId;

    private Long toUserId;

    @NotBlank(message = "Transfer type is required")
    private String transferType;

    @NotBlank(message = "Transfer reason is required")
    @Size(min = 10, max = 1000, message = "Transfer reason must be between 10 and 1000 characters")
    private String transferReason;

    private LocalDate expectedReturnDate;

    @NotNull(message = "Initiated by user ID is required")
    private Long initiatedByUserId;
}
