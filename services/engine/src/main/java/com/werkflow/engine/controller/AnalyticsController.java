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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Process execution statistics and task metrics — M6 Group A")
@SecurityRequirement(name = "bearer-jwt")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/process-stats")
    @Operation(summary = "Process execution stats", description = "Count, avg duration, success/failure rate per tenant")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ProcessStatsDTO> getProcessStats(
            @RequestParam(defaultValue = "default") String tenantId,
            @AuthenticationPrincipal Jwt jwt) {
        // HIGH-04: SUPER_ADMIN may pass any tenantId; all others are scoped to their own tenant
        String effectiveTenantId = hasSuperAdminRole(jwt) ? tenantId : extractTenant(jwt);
        return ResponseEntity.ok(analyticsService.getProcessStats(effectiveTenantId));
    }

    @GetMapping("/task-metrics")
    @Operation(summary = "Task metrics", description = "Avg cycle time, bottleneck step, SLA compliance per tenant")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<TaskMetricsDTO> getTaskMetrics(
            @RequestParam(defaultValue = "default") String tenantId,
            @AuthenticationPrincipal Jwt jwt) {
        // HIGH-04: SUPER_ADMIN may pass any tenantId; all others are scoped to their own tenant
        String effectiveTenantId = hasSuperAdminRole(jwt) ? tenantId : extractTenant(jwt);
        return ResponseEntity.ok(analyticsService.getTaskMetrics(effectiveTenantId));
    }

    private boolean hasSuperAdminRole(Jwt jwt) {
        Collection<?> authorities = jwt.getClaimAsStringList("roles");
        if (authorities != null && authorities.contains("SUPER_ADMIN")) return true;
        // Also check realm_access.roles
        java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof Collection<?> r) {
                return r.contains("SUPER_ADMIN") || r.contains("super_admin");
            }
        }
        return false;
    }

    private String extractTenant(Jwt jwt) {
        String tc = jwt.getClaimAsString("tenant_code");
        return (tc != null && !tc.isBlank()) ? tc : "default";
    }
}
