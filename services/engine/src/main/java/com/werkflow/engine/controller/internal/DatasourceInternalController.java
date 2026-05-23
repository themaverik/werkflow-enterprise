package com.werkflow.engine.controller.internal;

import com.werkflow.engine.action.credential.dto.DatasourceEvictRequest;
import com.werkflow.engine.action.db.DatasourceRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Engine-internal endpoint for cross-service datasource pool management.
 *
 * <p>Admin-service calls {@code POST /api/internal/datasources/evict} after a
 * datasource update or a {@code jdbc-password} credential rotation. The engine
 * closes and removes the stale HikariCP pool for the affected
 * {@code (tenantCode, ref)} pair; the next query will re-fetch the config from
 * admin and open a fresh pool with updated credentials from OpenBao.
 *
 * <p>Role-gated to {@code ADMIN_SERVICE} (the standard service-to-service
 * principal) and {@code SUPER_ADMIN} (manual operator override).
 */
@RestController
@RequestMapping("/api/internal/datasources")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal: Datasources",
     description = "Service-to-service datasource pool operations (M4.12 B.5)")
public class DatasourceInternalController {

    private final DatasourceRegistry datasourceRegistry;

    @PostMapping("/evict")
    @PreAuthorize("hasAnyRole('ADMIN_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: evict a tenant datasource pool",
        description = "Closes and removes the cached HikariCP pool for the given "
            + "(tenantId, ref) pair. The next query will re-fetch the datasource "
            + "config and re-open the pool with fresh credentials from OpenBao. "
            + "Called by admin-service after a datasource update or credential rotation."
    )
    public ResponseEntity<Void> evict(@Valid @RequestBody DatasourceEvictRequest request) {
        log.debug("Datasource pool evict: tenantId={} ref={}", request.tenantId(), request.ref());
        datasourceRegistry.evict(request.tenantId(), request.ref());
        return ResponseEntity.noContent().build();
    }
}
