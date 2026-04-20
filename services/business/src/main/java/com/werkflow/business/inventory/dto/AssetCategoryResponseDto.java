package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for AssetCategory response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCategoryResponseDto {

    private Long id;

    private String name;

    private String code;

    private String description;

    private Long parentCategoryId;

    private List<AssetCategoryResponseDto> childCategories;

    private String custodianDeptCode;

    private String custodianUserId;

    private List<AssetCategoryResponseDto> children;

    private Boolean requiresApproval;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
