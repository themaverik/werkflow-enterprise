package com.werkflow.engine.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Engine-internal endpoint for tenant lifecycle operations.
 *
 * <p>Used by admin-service to perform pre-deletion checks before removing a tenant.
 * Secured to {@code ADMIN_SERVICE} and {@code SUPER_ADMIN} roles (defence-in-depth,
 * same policy as other internal endpoints).
 */
@RestController
@RequestMapping("/api/internal/tenants")
@RequiredArgsConstructor
@Tag(name = "Internal: Tenants", description = "Service-to-service tenant lifecycle operations")
public class TenantInternalController {

    private final RuntimeService runtimeService;

    @GetMapping("/{tenantId}/running-count")
    @PreAuthorize("hasAnyRole('ADMIN_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: count active process instances for a tenant",
        description = "Returns the number of currently active (non-suspended, non-ended) process "
            + "instances scoped to the given tenant. Used by admin-service before tenant deletion."
    )
    public ResponseEntity<Map<String, Long>> getRunningCount(@PathVariable String tenantId) {
        long count = runtimeService.createProcessInstanceQuery()
                .processInstanceTenantId(tenantId)
                .active()
                .count();
        return ResponseEntity.ok(Map.of("count", count));
    }
}
