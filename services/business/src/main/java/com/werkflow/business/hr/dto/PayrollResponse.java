package com.werkflow.business.hr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Integer paymentMonth;
    private Integer paymentYear;
    private LocalDate paymentDate;
    private BigDecimal basicSalary;
    private BigDecimal allowances;
    private BigDecimal bonuses;
    private BigDecimal overtimePay;
    private BigDecimal taxDeduction;
    private BigDecimal insuranceDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;
    private String remarks;
    private Boolean isPaid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
