package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.DepartmentType;
import com.werkflow.business.hr.entity.OfficeLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Department responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {

    private Long id;
    private String name;
    private String code;
    private Long organizationId;
    private DepartmentType departmentType;
    private String leadUserId;
    private OfficeLocation officeLocation;
    private String departmentEmail;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
