package com.werkflow.business.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCategoryResponse {

    private Long id;
    private Long parentCategoryId;
    private String parentCategoryName;
    private String name;
    private String code;
    private String description;
    private String custodianDeptCode;
    private String custodianUserId;
    private Boolean requiresApproval;
    private Boolean active;
    private List<AssetCategoryResponse> childCategories;
    private List<AssetCategoryResponseDto> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
