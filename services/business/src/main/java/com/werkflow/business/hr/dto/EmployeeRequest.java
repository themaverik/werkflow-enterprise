package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.EmploymentStatus;
import com.werkflow.business.hr.entity.Gender;
import com.werkflow.business.hr.entity.OfficeLocation;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Employee creation and update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRequest {

    @NotNull(message = "Organization ID is required")
    private Long organizationId;

    private String keycloakUserId;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;

    private Gender gender;

    private String profilePhotoUrl;

    // Nullable — dept FK (for backward compat)
    private Long departmentId;

    private String departmentCode;

    @Min(value = 0, message = "DoA level must be between 0 and 4")
    @Max(value = 4, message = "DoA level must be between 0 and 4")
    private Integer doaLevel;

    private OfficeLocation officeLocation;

    @Size(max = 100, message = "Position cannot exceed 100 characters")
    private String position;

    private LocalDate dateOfJoining;

    @NotNull(message = "Employment status is required")
    private EmploymentStatus employmentStatus;

    @DecimalMin(value = "0.0", inclusive = false, message = "Salary must be greater than 0")
    private BigDecimal salary;
}
