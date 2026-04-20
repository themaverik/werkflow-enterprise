package com.werkflow.business.hr.controller;

import com.werkflow.business.hr.dto.PerformanceReviewRequest;
import com.werkflow.business.hr.dto.PerformanceReviewResponse;
import com.werkflow.business.hr.service.PerformanceReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/performance-reviews")
@RequiredArgsConstructor
@Tag(name = "Performance Reviews", description = "Performance review management APIs")
public class PerformanceReviewController {

    private final PerformanceReviewService reviewService;

    @GetMapping
    @Operation(summary = "Get all reviews", description = "Retrieve all performance reviews")
    public ResponseEntity<List<PerformanceReviewResponse>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get review by ID", description = "Retrieve a performance review by ID")
    public ResponseEntity<PerformanceReviewResponse> getReviewById(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getReviewById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get reviews by employee", description = "Retrieve all performance reviews for an employee")
    public ResponseEntity<List<PerformanceReviewResponse>> getReviewsByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(reviewService.getReviewsByEmployee(employeeId));
    }

    @PostMapping
    @Operation(summary = "Create review", description = "Create a new performance review")
    public ResponseEntity<PerformanceReviewResponse> createReview(@Valid @RequestBody PerformanceReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reviewService.createReview(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update review", description = "Update a performance review")
    public ResponseEntity<PerformanceReviewResponse> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceReviewRequest request) {
        return ResponseEntity.ok(reviewService.updateReview(id, request));
    }

    @PutMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge review", description = "Employee acknowledges the performance review")
    public ResponseEntity<PerformanceReviewResponse> acknowledgeReview(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.acknowledgeReview(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete review", description = "Delete a performance review")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
