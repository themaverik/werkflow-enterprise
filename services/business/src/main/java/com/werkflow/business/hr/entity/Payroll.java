package com.werkflow.business.hr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payroll Entity
 * Represents employee salary payments and deductions
 */
@Entity
@Table(name = "payrolls", schema = "hr_service",
    indexes = {
        @Index(name = "idx_payroll_employee", columnList = "employee_id"),
        @Index(name = "idx_payroll_date", columnList = "payment_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_month_year",
            columnNames = {"employee_id", "payment_month", "payment_year"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll extends BaseEntity {

    @NotNull(message = "Employee is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @NotNull(message = "Payment month is required")
    @Column(name = "payment_month", nullable = false)
    private Integer paymentMonth;

    @NotNull(message = "Payment year is required")
    @Column(name = "payment_year", nullable = false)
    private Integer paymentYear;

    @NotNull(message = "Payment date is required")
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @NotNull(message = "Basic salary is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Basic salary must be greater than 0")
    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "allowances", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "bonuses", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal bonuses = BigDecimal.ZERO;

    @Column(name = "overtime_pay", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(name = "tax_deduction", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxDeduction = BigDecimal.ZERO;

    @Column(name = "insurance_deduction", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal insuranceDeduction = BigDecimal.ZERO;

    @Column(name = "other_deductions", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "gross_salary", precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "net_salary", precision = 15, scale = 2)
    private BigDecimal netSalary;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "is_paid")
    @Builder.Default
    private Boolean isPaid = false;

    // Calculate gross and net salary
    @PrePersist
    @PreUpdate
    private void calculateSalaries() {
        // Gross Salary = Basic Salary + Allowances + Bonuses + Overtime Pay
        this.grossSalary = basicSalary
            .add(allowances)
            .add(bonuses)
            .add(overtimePay);

        // Net Salary = Gross Salary - Tax - Insurance - Other Deductions
        this.netSalary = grossSalary
            .subtract(taxDeduction)
            .subtract(insuranceDeduction)
            .subtract(otherDeductions);
    }
}
