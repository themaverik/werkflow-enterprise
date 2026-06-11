package com.werkflow.engine.controller.internal;

import com.werkflow.engine.dto.SeedResult;
import com.werkflow.engine.service.ExampleSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Engine-internal endpoint for seeding example workflow artifacts into a tenant (ADR-031 Phase C).
 *
 * <p>Admin-service calls {@code POST /api/internal/examples/seed/{tenantId}} after provisioning
 * a new tenant (when {@code seedExamples=true} is requested). The engine resolves the example
 * files from {@code classpath:examples/tenants/{tenantId}/} with fallback to
 * {@code examples/tenants/default/}, then deploys forms, DMNs, and BPMNs in order.
 *
 * <p>The operation is idempotent: artefacts that already exist are skipped. Calling the endpoint
 * multiple times is safe.
 *
 * <p>Role-gated to {@code ADMIN_SERVICE} (the standard service-to-service principal) and
 * {@code SUPER_ADMIN} (manual operator override).
 */
@RestController
@RequestMapping("/api/internal/examples")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal: Examples",
     description = "Service-to-service example seeding operations (ADR-031 Phase C)")
public class ExamplesInternalController {

    private final ExampleSeedService exampleSeedService;

    @PostMapping("/seed/{tenantId}")
    @PreAuthorize("hasAnyRole('ADMIN_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: seed example workflows for a tenant",
        description = "Deploys the curated set of example BPMN, DMN, and form artefacts for the "
            + "given tenant. Artefacts already present are skipped (idempotent). "
            + "Called by admin-service after tenant provisioning when seedExamples=true."
    )
    public ResponseEntity<SeedResult> seed(@PathVariable String tenantId) {
        log.debug("Example seeding requested for tenant '{}'", tenantId);
        SeedResult result = exampleSeedService.seedForTenant(tenantId);
        return ResponseEntity.ok(result);
    }
}
