package com.werkflow.business.hr.service;

import com.werkflow.business.hr.dto.PerformanceReviewRequest;
import com.werkflow.business.hr.dto.PerformanceReviewResponse;
import com.werkflow.business.hr.entity.Employee;
import com.werkflow.business.hr.entity.PerformanceReview;
import com.werkflow.business.hr.repository.EmployeeRepository;
import com.werkflow.business.hr.repository.PerformanceReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PerformanceReviewService {

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;

    public List<PerformanceReviewResponse> getAllReviews() {
        return reviewRepository.findAll().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public PerformanceReviewResponse getReviewById(Long id) {
        PerformanceReview review = reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Performance review not found"));
        return convertToResponse(review);
    }

    public List<PerformanceReviewResponse> getReviewsByEmployee(Long employeeId) {
        return reviewRepository.findByEmployeeIdOrderByReviewDateDesc(employeeId)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public PerformanceReviewResponse createReview(PerformanceReviewRequest request) {
        log.info("Creating performance review for employee: {}", request.getEmployeeId());

        Employee employee = employeeRepository.findById(request.getEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Employee reviewer = employeeRepository.findById(request.getReviewerId())
            .orElseThrow(() -> new EntityNotFoundException("Reviewer not found"));

        PerformanceReview review = PerformanceReview.builder()
            .employee(employee)
            .reviewDate(request.getReviewDate())
            .reviewPeriodStart(request.getReviewPeriodStart())
            .reviewPeriodEnd(request.getReviewPeriodEnd())
            .rating(request.getRating())
            .score(request.getScore())
            .strengths(request.getStrengths())
            .areasForImprovement(request.getAreasForImprovement())
            .goals(request.getGoals())
            .comments(request.getComments())
            .reviewer(reviewer)
            .employeeAcknowledged(false)
            .build();

        PerformanceReview saved = reviewRepository.save(review);
        log.info("Performance review created with id: {}", saved.getId());
        return convertToResponse(saved);
    }

    @Transactional
    public PerformanceReviewResponse updateReview(Long id, PerformanceReviewRequest request) {
        PerformanceReview review = reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Performance review not found"));

        review.setReviewDate(request.getReviewDate());
        review.setReviewPeriodStart(request.getReviewPeriodStart());
        review.setReviewPeriodEnd(request.getReviewPeriodEnd());
        review.setRating(request.getRating());
        review.setScore(request.getScore());
        review.setStrengths(request.getStrengths());
        review.setAreasForImprovement(request.getAreasForImprovement());
        review.setGoals(request.getGoals());
        review.setComments(request.getComments());

        return convertToResponse(reviewRepository.save(review));
    }

    @Transactional
    public PerformanceReviewResponse acknowledgeReview(Long id) {
        PerformanceReview review = reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Performance review not found"));

        review.setEmployeeAcknowledged(true);
        review.setAcknowledgedAt(LocalDate.now());

        return convertToResponse(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }

    private PerformanceReviewResponse convertToResponse(PerformanceReview review) {
        return PerformanceReviewResponse.builder()
            .id(review.getId())
            .employeeId(review.getEmployee().getId())
            .employeeName(review.getEmployee().getFullName())
            .reviewDate(review.getReviewDate())
            .reviewPeriodStart(review.getReviewPeriodStart())
            .reviewPeriodEnd(review.getReviewPeriodEnd())
            .rating(review.getRating())
            .score(review.getScore())
            .strengths(review.getStrengths())
            .areasForImprovement(review.getAreasForImprovement())
            .goals(review.getGoals())
            .comments(review.getComments())
            .reviewerId(review.getReviewer().getId())
            .reviewerName(review.getReviewer().getFullName())
            .employeeAcknowledged(review.getEmployeeAcknowledged())
            .acknowledgedAt(review.getAcknowledgedAt())
            .createdAt(review.getCreatedAt())
            .updatedAt(review.getUpdatedAt())
            .build();
    }
}
