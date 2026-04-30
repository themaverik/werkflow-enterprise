package com.werkflow.engine.controller;

import com.werkflow.engine.dto.DeadLetterJobResponse;
import com.werkflow.engine.service.JobManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for monitoring and managing Flowable dead-letter jobs.
 * Dead-letter jobs are async jobs that have exhausted all retry attempts.
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "Dead-letter job monitoring and recovery")
@SecurityRequirement(name = "bearer-jwt")
public class JobManagementController {

    private final JobManagementService jobManagementService;

    @GetMapping("/dead-letter")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "List dead-letter jobs", description = "Returns all async jobs that have exhausted retry attempts")
    public ResponseEntity<List<DeadLetterJobResponse>> listDeadLetterJobs(
            @Parameter(description = "Filter by tenant ID (optional)")
            @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(jobManagementService.listDeadLetterJobs(tenantId));
    }

    public record RetryRequest(int retries) {}

    @PostMapping("/dead-letter/{jobId}/retry")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Retry a dead-letter job", description = "Moves the job back to the executable queue")
    public ResponseEntity<Void> retryJob(
            @PathVariable String jobId,
            @RequestBody(required = false) RetryRequest request) {
        if (request != null && request.retries() > 0) {
            jobManagementService.retryJob(jobId, request.retries());
        } else {
            jobManagementService.retryJob(jobId);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/dead-letter/{jobId}")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Delete a dead-letter job", description = "Permanently removes the job — cannot be undone")
    public ResponseEntity<Void> deleteJob(@PathVariable String jobId) {
        jobManagementService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }
}
