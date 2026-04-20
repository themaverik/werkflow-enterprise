package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.DepartmentType;
import com.werkflow.business.hr.entity.OfficeLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Department creation and update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRequest {

    @NotBlank(message = "Department name is required")
    @Size(min = 2, max = 100, message = "Department name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Department code is required")
    @Size(max = 50, message = "Code cannot exceed 50 characters")
    private String code;

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    @NotNull(message = "Department type is required")
    private DepartmentType departmentType;

    private String leadUserId;

    private OfficeLocation officeLocation;

    private String departmentEmail;

    private Boolean isActive;
}
