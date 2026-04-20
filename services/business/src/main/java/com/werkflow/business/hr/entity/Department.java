package com.werkflow.business.hr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Department Entity
 * Represents organizational departments in the HR module.
 */
@Entity
@Table(name = "departments", schema = "hr_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEntity {

    @NotBlank(message = "Department name is required")
    @Size(min = 2, max = 100, message = "Department name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Department code is required")
    @Size(max = 50, message = "Code cannot exceed 50 characters")
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotNull(message = "Organization ID is required")
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @NotNull(message = "Department type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "department_type", nullable = false)
    private DepartmentType departmentType;

    @Column(name = "lead_user_id")
    private String leadUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "office_location")
    private OfficeLocation officeLocation;

    @Column(name = "department_email")
    private String departmentEmail;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
