package com.werkflow.business.hr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Performance Review Entity
 * Represents employee performance evaluations
 */
@Entity
@Table(name = "performance_reviews", schema = "hr_service", indexes = {
    @Index(name = "idx_review_employee", columnList = "employee_id"),
    @Index(name = "idx_review_date", columnList = "review_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReview extends BaseEntity {

    @NotNull(message = "Employee is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @NotNull(message = "Review date is required")
    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @NotNull(message = "Review period start is required")
    @Column(name = "review_period_start", nullable = false)
    private LocalDate reviewPeriodStart;

    @NotNull(message = "Review period end is required")
    @Column(name = "review_period_end", nullable = false)
    private LocalDate reviewPeriodEnd;

    @NotNull(message = "Rating is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 30)
    private PerformanceRating rating;

    @NotNull(message = "Score is required")
    @DecimalMin(value = "0.0", message = "Score must be at least 0")
    @DecimalMax(value = "100.0", message = "Score cannot exceed 100")
    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Size(max = 2000, message = "Strengths cannot exceed 2000 characters")
    @Column(name = "strengths", length = 2000)
    private String strengths;

    @Size(max = 2000, message = "Areas for improvement cannot exceed 2000 characters")
    @Column(name = "areas_for_improvement", length = 2000)
    private String areasForImprovement;

    @Size(max = 2000, message = "Goals cannot exceed 2000 characters")
    @Column(name = "goals", length = 2000)
    private String goals;

    @Size(max = 2000, message = "Comments cannot exceed 2000 characters")
    @Column(name = "comments", length = 2000)
    private String comments;

    @NotNull(message = "Reviewer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private Employee reviewer;

    @Column(name = "employee_acknowledged")
    @Builder.Default
    private Boolean employeeAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDate acknowledgedAt;

    // Validation method
    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (reviewPeriodEnd != null && reviewPeriodStart != null
            && reviewPeriodEnd.isBefore(reviewPeriodStart)) {
            throw new IllegalArgumentException("Review period end date cannot be before start date");
        }
    }
}
