package com.werkflow.business.hr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Employee Entity
 * Represents employees in the organization.
 * Maps to hr_service.employees with new schema from V4 migration.
 */
@Entity
@Table(name = "employees", schema = "hr_service", indexes = {
    @Index(name = "idx_employee_email", columnList = "email"),
    @Index(name = "idx_employee_org", columnList = "organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseEntity {

    @NotNull(message = "Organization ID is required")
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Column(name = "phone_number", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    // Nullable FK — department_id kept for backward compat with JPA relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // Denormalized department code for cross-service / DoA queries
    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "doa_level")
    @Builder.Default
    private Integer doaLevel = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "office_location", length = 30)
    private OfficeLocation officeLocation;

    @Size(max = 100, message = "Position cannot exceed 100 characters")
    @Column(name = "job_title", length = 100)
    private String position;

    @Column(name = "join_date")
    private LocalDate dateOfJoining;

    @NotNull(message = "Employment status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    @Builder.Default
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @DecimalMin(value = "0.0", inclusive = false, message = "Salary must be greater than 0")
    @Column(name = "salary", precision = 15, scale = 2)
    private BigDecimal salary;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Leave> leaves = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Attendance> attendances = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PerformanceReview> performanceReviews = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Payroll> payrolls = new ArrayList<>();

    // Convenience accessor for department ID (used in service/response mapping)
    @Transient
    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @PrePersist
    @PreUpdate
    void validateDoaLevel() {
        if (doaLevel == null) doaLevel = 0;
        if (doaLevel < 0 || doaLevel > 4) {
            throw new IllegalArgumentException("doaLevel must be between 0 and 4, got: " + doaLevel);
        }
    }
}
