package com.werkflow.admin.controller;

import com.werkflow.admin.dto.datasource.DatasourceEngineConfig;
import com.werkflow.admin.dto.datasource.DatasourceTestResult;
import com.werkflow.admin.dto.datasource.TenantDatasourceRequest;
import com.werkflow.admin.dto.datasource.TenantDatasourceResponse;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.TenantDatasourceService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for managing tenant datasource registrations.
 *
 * <p>Tenant isolation is enforced at the service layer — callers can only access
 * datasources belonging to their own tenant (resolved from the JWT).</p>
 *
 * <p>The engine-internal endpoint {@code GET /{tenantCode}/{ref}} requires the
 * {@code ENGINE_SERVICE} role or {@code SUPER_ADMIN}. It returns only the
 * {@link TenantDatasourceResponse} (no resolved password); the engine resolves
 * the credential locally via its own SecretsResolver (Fix C-3).</p>
 *
 * <p>The test endpoint is rate-limited to 5 calls per 60 seconds per JVM instance
 * to prevent brute-force host probing (Fix H-3).</p>
 */
@RestController
@RequestMapping("/api/v1/config/datasources")
@RequiredArgsConstructor
@Tag(name = "Tenant Datasources", description = "JDBC datasource registration and connection testing for database connectors")
public class DatasourceController {

    private final TenantDatasourceService datasourceService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // -------------------------------------------------------------------------
    // Tenant-scoped CRUD
    // -------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "List all datasources for the caller's tenant")
    public ResponseEntity<List<TenantDatasourceResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(datasourceService.list(tenantId));
    }

    @GetMapping("/{ref}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get a single datasource by ref")
    public ResponseEntity<TenantDatasourceResponse> get(
            @PathVariable String ref,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(datasourceService.get(tenantId, ref));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Register a new datasource")
    public ResponseEntity<TenantDatasourceResponse> create(
            @Valid @RequestBody TenantDatasourceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TenantDatasourceResponse response = datasourceService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{ref}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Update a datasource registration")
    public ResponseEntity<TenantDatasourceResponse> update(
            @PathVariable String ref,
            @Valid @RequestBody TenantDatasourceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(datasourceService.update(tenantId, ref, request));
    }

    @DeleteMapping("/{ref}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Delete a datasource registration")
    public ResponseEntity<Void> delete(
            @PathVariable String ref,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        datasourceService.delete(tenantId, ref);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tests the live connection for a registered datasource.
     *
     * <p>Rate-limited to 5 requests per 60 seconds to prevent brute-force host
     * probing via the test endpoint (Fix H-3).</p>
     */
    @PostMapping("/{ref}/test")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Test connection for a datasource",
               description = "Establishes a throw-away non-pooled connection and runs SELECT 1. Returns latency and DB version on success.")
    @RateLimiter(name = "datasource-test", fallbackMethod = "testConnectionRateLimited")
    public ResponseEntity<DatasourceTestResult> test(
            @PathVariable String ref,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        DatasourceTestResult result = datasourceService.testConnection(tenantId, ref);
        return ResponseEntity.ok(result);
    }

    /**
     * Fallback invoked when the rate limiter rejects a test-connection request.
     */
    public ResponseEntity<DatasourceTestResult> testConnectionRateLimited(
            String ref, Jwt jwt, RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new DatasourceTestResult(false,
                "Too many connection tests — try again in 60 seconds", 0));
    }

    // -------------------------------------------------------------------------
    // Engine-internal endpoint — used by DatasourceRegistry in the engine service
    // -------------------------------------------------------------------------

    /**
     * Returns the datasource configuration for the engine's DatasourceRegistry.
     *
     * <p>Returns only non-secret config plus {@code credentialRef}; the engine resolves
     * the credential from OpenBao directly. The plaintext credential never traverses the wire.</p>
     */
    @GetMapping("/{tenantCode}/{ref}")
    @PreAuthorize("hasAnyRole('ENGINE_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: get datasource config for engine",
        description = "Called by the engine's DatasourceRegistry to build HikariCP pools. " +
                      "Returns non-secret config plus credentialRef. " +
                      "The engine resolves the credential from OpenBao. " +
                      "Requires ENGINE_SERVICE role (service-to-service JWT) or SUPER_ADMIN."
    )
    public ResponseEntity<DatasourceEngineConfig> resolveForEngine(
            @PathVariable String tenantCode,
            @PathVariable String ref) {
        DatasourceEngineConfig config = datasourceService.resolveForEngine(tenantCode, ref);
        return ResponseEntity.ok(config);
    }
}
