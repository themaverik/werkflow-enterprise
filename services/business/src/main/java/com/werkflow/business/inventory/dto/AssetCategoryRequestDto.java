package com.werkflow.business.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AssetCategory creation and update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCategoryRequestDto {

    @NotBlank(message = "Category name is required")
    @Size(min = 3, max = 100, message = "Category name must be between 3 and 100 characters")
    private String name;

    @Size(max = 50, message = "Code cannot exceed 50 characters")
    private String code;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    private Long parentCategoryId;

    private String custodianDeptCode;

    private String custodianUserId;

    @Builder.Default
    private Boolean requiresApproval = true;

    @Builder.Default
    private Boolean active = true;
}
