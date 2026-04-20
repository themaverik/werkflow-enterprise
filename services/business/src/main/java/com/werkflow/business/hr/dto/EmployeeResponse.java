package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.EmploymentStatus;
import com.werkflow.business.hr.entity.Gender;
import com.werkflow.business.hr.entity.OfficeLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for Employee responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {

    private Long id;
    private Long organizationId;
    private String keycloakUserId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private Gender gender;
    private String profilePhotoUrl;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private Integer doaLevel;
    private OfficeLocation officeLocation;
    private String position;
    private LocalDate dateOfJoining;
    private EmploymentStatus employmentStatus;
    private BigDecimal salary;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
