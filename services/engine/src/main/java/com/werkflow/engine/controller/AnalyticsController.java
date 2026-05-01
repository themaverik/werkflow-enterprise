package com.werkflow.engine.controller;

import com.werkflow.engine.dto.ProcessStatsDTO;
import com.werkflow.engine.dto.TaskMetricsDTO;
import com.werkflow.engine.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Process execution statistics and task metrics — M6 Group A")
@SecurityRequirement(name = "bearer-jwt")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/process-stats")
    @Operation(summary = "Process execution stats", description = "Count, avg duration, success/failure rate per tenant")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ProcessStatsDTO> getProcessStats(
            @RequestParam(defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(analyticsService.getProcessStats(tenantId));
    }

    @GetMapping("/task-metrics")
    @Operation(summary = "Task metrics", description = "Avg cycle time, bottleneck step, SLA compliance per tenant")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<TaskMetricsDTO> getTaskMetrics(
            @RequestParam(defaultValue = "default") String tenantId) {
        return ResponseEntity.ok(analyticsService.getTaskMetrics(tenantId));
    }
}
