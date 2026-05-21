package com.werkflow.admin.controller;

import com.werkflow.admin.dto.credential.CreateTenantCredentialRequest;
import com.werkflow.admin.dto.credential.CredentialPathResponse;
import com.werkflow.admin.dto.credential.CredentialTestResultResponse;
import com.werkflow.admin.dto.credential.TenantCredentialResponse;
import com.werkflow.admin.dto.credential.UpdateTenantCredentialRequest;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.TenantCredentialService;
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
import java.util.UUID;

/**
 * REST endpoints for tenant credential registrations (M4.12 Phase B.2).
 *
 * <p>Tenant CRUD endpoints derive the tenant from the JWT. Credential VALUES are
 * never returned — the response carries metadata + field names only.
 *
 * <p>The engine-internal lookup at {@code GET /{tenantCode}/{type}/{label}}
 * returns the OpenBao path so the engine can read Vault directly with its own
 * read-only token. Requires {@code ROLE_ENGINE_SERVICE} (service-to-service JWT)
 * or {@code SUPER_ADMIN}.
 *
 * <p>Test-connection is intentionally NOT in this controller — the
 * {@code CredentialType.validate} contract lives in engine; B.2 step 5 adds the
 * cross-service wiring.
 */
@RestController
@RequestMapping("/api/v1/config/credentials")
@RequiredArgsConstructor
@Tag(name = "Tenant Credentials",
     description = "Per-tenant credential registrations backed by OpenBao (M4.12 B.2)")
public class TenantCredentialController {

    private final TenantCredentialService credentialService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // -- Tenant-scoped CRUD --------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "List all credentials for the caller's tenant")
    public ResponseEntity<List<TenantCredentialResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(credentialService.list(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Get a credential's metadata by id")
    public ResponseEntity<TenantCredentialResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(credentialService.get(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Register a new credential — writes values to OpenBao + inserts metadata row")
    public ResponseEntity<TenantCredentialResponse> create(
            @Valid @RequestBody CreateTenantCredentialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TenantCredentialResponse response = credentialService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Rotate a credential's values — writes a new Vault version")
    public ResponseEntity<TenantCredentialResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantCredentialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(credentialService.update(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Remove a credential — deletes the DB row and soft-deletes the Vault entry")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        credentialService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
    @Operation(
        summary = "Test a credential's connection by delegating to engine",
        description = "Engine resolves the credential from OpenBao and runs CredentialType.validate. "
            + "Plaintext values never traverse the wire — only the boolean+message outcome."
    )
    public ResponseEntity<CredentialTestResultResponse> test(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(credentialService.testConnection(tenantId, id));
    }

    // -- Engine-internal lookup ----------------------------------------------

    /**
     * Resolves the OpenBao path for a credential triple. Engine reads Vault
     * directly with its read-only token after this lookup.
     *
     * <p>Returns 404 when no metadata row exists; does not leak whether the
     * credential type/label was wrong vs the whole tenant having no credentials.
     */
    @GetMapping("/{tenantCode}/{credentialType}/{label}")
    @PreAuthorize("hasAnyRole('ENGINE_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: resolve OpenBao path for a credential triple",
        description = "Called by engine's CredentialMetadataClient to locate the Vault path. "
            + "Returns metadata only; the engine reads Vault directly. "
            + "Requires ENGINE_SERVICE role (service-to-service JWT) or SUPER_ADMIN."
    )
    public ResponseEntity<CredentialPathResponse> resolveForEngine(
            @PathVariable String tenantCode,
            @PathVariable String credentialType,
            @PathVariable String label) {
        return ResponseEntity.ok(credentialService.resolvePath(tenantCode, credentialType, label));
    }
}
