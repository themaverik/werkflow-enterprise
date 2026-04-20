package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.PerformanceRating;
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
public class PerformanceReviewResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate reviewDate;
    private LocalDate reviewPeriodStart;
    private LocalDate reviewPeriodEnd;
    private PerformanceRating rating;
    private BigDecimal score;
    private String strengths;
    private String areasForImprovement;
    private String goals;
    private String comments;
    private Long reviewerId;
    private String reviewerName;
    private Boolean employeeAcknowledged;
    private LocalDate acknowledgedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
