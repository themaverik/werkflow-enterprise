package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.LeaveStatus;
import com.werkflow.business.hr.entity.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numberOfDays;
    private String reason;
    private LeaveStatus status;
    private Long approvedById;
    private String approvedByName;
    private LocalDate approvedAt;
    private String approvalRemarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
