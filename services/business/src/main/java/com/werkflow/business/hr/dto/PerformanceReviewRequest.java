package com.werkflow.business.hr.dto;

import com.werkflow.business.hr.entity.PerformanceRating;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceReviewRequest {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Review date is required")
    private LocalDate reviewDate;

    @NotNull(message = "Review period start is required")
    private LocalDate reviewPeriodStart;

    @NotNull(message = "Review period end is required")
    private LocalDate reviewPeriodEnd;

    @NotNull(message = "Rating is required")
    private PerformanceRating rating;

    @NotNull(message = "Score is required")
    @DecimalMin(value = "0.0", message = "Score must be at least 0")
    @DecimalMax(value = "100.0", message = "Score cannot exceed 100")
    private BigDecimal score;

    @Size(max = 2000, message = "Strengths cannot exceed 2000 characters")
    private String strengths;

    @Size(max = 2000, message = "Areas for improvement cannot exceed 2000 characters")
    private String areasForImprovement;

    @Size(max = 2000, message = "Goals cannot exceed 2000 characters")
    private String goals;

    @Size(max = 2000, message = "Comments cannot exceed 2000 characters")
    private String comments;

    @NotNull(message = "Reviewer ID is required")
    private Long reviewerId;
}
