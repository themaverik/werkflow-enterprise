package com.werkflow.engine.controller.internal;

import com.werkflow.engine.action.credential.CredentialRegistry;
import com.werkflow.engine.action.credential.CredentialResolutionException;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.TestResult;
import com.werkflow.engine.action.credential.dto.CredentialTestRequest;
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
 * Engine-internal endpoint for cross-service credential testing.
 *
 * <p>Admin-service calls {@code POST /api/internal/credentials/test} when an
 * admin user clicks "Test connection" in the portal. The engine reads the
 * tenant credential from OpenBao, invokes the credential type's
 * {@link CredentialType#validate} contract, and returns the result. Plaintext
 * values never traverse the wire — only the test outcome.
 *
 * <p>Role-gated to {@code ADMIN_SERVICE} (the standard service-to-service
 * principal) and {@code SUPER_ADMIN} (manual operator override).
 */
@RestController
@RequestMapping("/api/internal/credentials")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal: Credentials",
     description = "Service-to-service credential operations (M4.12 B.2)")
public class CredentialInternalController {

    private final CredentialRegistry credentialRegistry;

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ADMIN_SERVICE','SUPER_ADMIN')")
    @Operation(
        summary = "Internal: validate a tenant credential",
        description = "Resolves the credential via OpenBao and calls its validate() contract. "
            + "Plaintext values are never returned. Called by admin-service from the "
            + "POST /api/v1/config/credentials/{id}/test endpoint."
    )
    public ResponseEntity<TestResult> test(@Valid @RequestBody CredentialTestRequest request) {
        log.debug("Credential test: tenant={} type={} label={}",
            request.tenantId(), request.credentialType(), request.label());
        try {
            CredentialType type = credentialRegistry.get(request.credentialType());
            CredentialValues values = credentialRegistry.resolveForTenant(
                request.credentialType(), request.tenantId(), request.label());
            return ResponseEntity.ok(type.validate(values));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(TestResult.error("Unknown credential type: "
                + request.credentialType()));
        } catch (CredentialResolutionException ex) {
            log.warn("Credential test failed for tenant={} type={} label={}: {}",
                request.tenantId(), request.credentialType(), request.label(), ex.getMessage());
            return ResponseEntity.ok(TestResult.error("Credential resolution failed"));
        }
    }
}
