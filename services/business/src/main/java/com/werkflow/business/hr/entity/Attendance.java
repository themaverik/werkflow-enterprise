package com.werkflow.business.hr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Attendance Entity
 * Represents daily employee attendance records
 */
@Entity
@Table(name = "attendances", schema = "hr_service",
    indexes = {
        @Index(name = "idx_attendance_employee", columnList = "employee_id"),
        @Index(name = "idx_attendance_date", columnList = "attendance_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_date", columnNames = {"employee_id", "attendance_date"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance extends BaseEntity {

    @NotNull(message = "Employee is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @NotNull(message = "Attendance date is required")
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Size(max = 500, message = "Remarks cannot exceed 500 characters")
    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "worked_hours")
    private Double workedHours;

    // Helper method to calculate worked hours
    @PrePersist
    @PreUpdate
    private void calculateWorkedHours() {
        if (checkInTime != null && checkOutTime != null) {
            long minutesWorked = java.time.Duration.between(checkInTime, checkOutTime).toMinutes();
            this.workedHours = minutesWorked / 60.0;
        }
    }
}
