package com.werkflow.admin.designtime.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a tenant category.
 */
public record CategoryRequest(
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9-]+$",
                message = "code must be lowercase alphanumeric with hyphens") String code,
        @Size(max = 50) String icon,
        @Size(max = 20) String color,
        Integer displayOrder
) {}
