package com.werkflow.admin.controller;

import com.werkflow.admin.dto.connector.*;
import com.werkflow.admin.dto.connector.ConnectorUpdateRequest;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.ConnectorService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
@Tag(name = "Connectors", description = "Connector management APIs")
public class ConnectorController {

    private final ConnectorService connectorService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Resolves the tenant code from the request parameter if provided (SUPER_ADMIN cross-tenant use),
     * otherwise falls back to the tenant_id claim in the caller's JWT.
     */
    private String resolveTenant(String tenantCode, Jwt jwt) {
        return (tenantCode != null && !tenantCode.isBlank()) ? tenantCode : jwtClaimsExtractor.getTenantId(jwt);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List connectors", description = "Get all connectors for a tenant. tenantCode is optional — defaults to caller's JWT tenant_id claim. SUPER_ADMIN may pass tenantCode to query a different tenant.")
    public ResponseEntity<List<ConnectorResponse>> list(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(connectorService.listByTenant(resolveTenant(tenantCode, jwt)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create connector", description = "Create a new connector (ADMIN, SUPER_ADMIN). tenantCode in the request body is optional — if blank, resolved from the caller's JWT tenant_id claim.")
    public ResponseEntity<ConnectorResponse> create(
            @Valid @RequestBody ConnectorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (request.getTenantCode() == null || request.getTenantCode().isBlank()) {
            request.setTenantCode(jwtClaimsExtractor.getTenantId(jwt));
        }
        ConnectorResponse response = connectorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{connectorKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update connector", description = "Update connector configuration (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> update(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectorResponse response = connectorService.update(resolveTenant(tenantCode, jwt), connectorKey, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{connectorKey}/schema")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update connector schema", description = "Update the sample schema for a connector (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> updateSchema(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @RequestBody String sampleSchema,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectorResponse response = connectorService.updateSampleSchema(resolveTenant(tenantCode, jwt), connectorKey, sampleSchema);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{connectorKey}/endpoints/{endpointId}/schema")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update endpoint schema", description = "Update the sample schema for a specific endpoint by ID (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> updateEndpointSchema(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @PathVariable Long endpointId,
            @RequestBody String sampleSchema,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectorResponse response = connectorService.updateEndpointSchema(resolveTenant(tenantCode, jwt), endpointId, sampleSchema);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{connectorKey}/schema")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get connector schema", description = "Returns the sampleSchema JSON for a connector")
    public ResponseEntity<String> getSchema(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.getSchema(resolveTenant(tenantCode, jwt), connectorKey)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/internal/endpoints/resolve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ENGINE_SERVICE')")
    @Operation(summary = "Resolve connector endpoint", description = "Resolve the base URL for a connector key scoped to a tenant and environment (internal use). tenantCode required for ENGINE_SERVICE callers; optional for ADMIN/SUPER_ADMIN (defaults to JWT tenant).")
    public ResponseEntity<String> resolveEndpoint(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @RequestParam String connectorKey,
            @RequestParam(defaultValue = "development") String environment,
            @AuthenticationPrincipal Jwt jwt) {
        return connectorService.resolveBaseUrl(resolveTenant(tenantCode, jwt), connectorKey, environment)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{connectorKey}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Test connector call", description = "Test a connector call with provided request (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorTestResponse> testCall(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectorTestResponse response = connectorService.testCall(resolveTenant(tenantCode, jwt), connectorKey, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{connectorKey}/call")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ENGINE_SERVICE')")
    @Operation(summary = "Connector proxy call", description = "Outbound proxy — forwards call to external connector, returns { statusCode, body, headers, durationMs }. Used by portal proxy and engine delegate. tenantCode optional — defaults to JWT tenant_id.")
    public ResponseEntity<ConnectorTestResponse> call(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @RequestParam String path,
            @RequestParam(defaultValue = "GET") String method,
            @RequestBody(required = false) String requestBody,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectorTestResponse response = connectorService.callConnector(resolveTenant(tenantCode, jwt), connectorKey, path, method, requestBody);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{connectorKey}/endpoints")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Add connector endpoint", description = "Adds a new environment endpoint to an existing connector (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> addEndpoint(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorEndpointRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            connectorService.addEndpoint(resolveTenant(tenantCode, jwt), connectorKey, request));
    }

    @DeleteMapping("/{connectorKey}/endpoints/{endpointId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Delete connector endpoint", description = "Removes a specific environment endpoint. Cannot delete the last endpoint — use DELETE /{connectorKey} instead.")
    public ResponseEntity<Void> deleteEndpoint(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @PathVariable Long endpointId,
            @AuthenticationPrincipal Jwt jwt) {
        connectorService.deleteEndpoint(resolveTenant(tenantCode, jwt), endpointId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{connectorKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Delete connector", description = "Permanently removes connector endpoint and credential (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<Void> delete(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @AuthenticationPrincipal Jwt jwt) {
        connectorService.delete(resolveTenant(tenantCode, jwt), connectorKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectorKey}/api-key")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Register ERP API key", description = "Hashes rawKey, registers with ERP, stores encrypted for outbound calls. Shows raw key once — never retrievable after.")
    public ResponseEntity<Map<String, String>> registerApiKey(
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorApiKeyRequest request,
            @RequestHeader("Authorization") String authorization,
            @AuthenticationPrincipal Jwt jwt) {
        connectorService.registerApiKey(resolveTenant(tenantCode, jwt), connectorKey, request, authorization);
        return ResponseEntity.ok(Map.of("status", "registered"));
    }
}
