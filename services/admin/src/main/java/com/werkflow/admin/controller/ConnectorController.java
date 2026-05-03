package com.werkflow.admin.controller;

import com.werkflow.admin.dto.connector.*;
import com.werkflow.admin.dto.connector.ConnectorUpdateRequest;
import com.werkflow.admin.service.ConnectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
@Tag(name = "Connectors", description = "Connector management APIs")
public class ConnectorController {

    private final ConnectorService connectorService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List connectors", description = "Get all connectors for a tenant (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<List<ConnectorResponse>> list(@RequestParam String tenantCode) {
        return ResponseEntity.ok(connectorService.listByTenant(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create connector", description = "Create a new connector (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> create(@Valid @RequestBody ConnectorRequest request) {
        ConnectorResponse response = connectorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{connectorKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update connector", description = "Update connector configuration (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> update(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorUpdateRequest request) {
        ConnectorResponse response = connectorService.update(tenantCode, connectorKey, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{connectorKey}/schema")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update connector schema", description = "Update the sample schema for a connector (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorResponse> updateSchema(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey,
            @RequestBody String sampleSchema) {
        ConnectorResponse response = connectorService.updateSampleSchema(tenantCode, connectorKey, sampleSchema);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{connectorKey}/schema")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get connector schema", description = "Returns the sampleSchema JSON for a connector")
    public ResponseEntity<String> getSchema(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey) {
        return connectorService.getSchema(tenantCode, connectorKey)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/internal/endpoints/resolve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ENGINE_SERVICE')")
    @Operation(summary = "Resolve connector endpoint", description = "Resolve the base URL for a connector key scoped to a tenant and environment (internal use)")
    public ResponseEntity<String> resolveEndpoint(
            @RequestParam String tenantCode,
            @RequestParam String connectorKey,
            @RequestParam(defaultValue = "development") String environment) {
        return connectorService.resolveBaseUrl(tenantCode, connectorKey, environment)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{connectorKey}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Test connector call", description = "Test a connector call with provided request (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<ConnectorTestResponse> testCall(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorTestRequest request) {
        ConnectorTestResponse response = connectorService.testCall(tenantCode, connectorKey, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{connectorKey}/call")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ENGINE_SERVICE')")
    @Operation(summary = "Connector proxy call", description = "Outbound proxy — forwards call to external connector, returns { statusCode, body, headers, durationMs }. Used by portal proxy and engine delegate.")
    public ResponseEntity<ConnectorTestResponse> call(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey,
            @RequestParam String path,
            @RequestParam(defaultValue = "GET") String method,
            @RequestBody(required = false) String requestBody) {
        ConnectorTestResponse response = connectorService.callConnector(tenantCode, connectorKey, path, method, requestBody);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{connectorKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Delete connector", description = "Permanently removes connector endpoint and credential (ADMIN, SUPER_ADMIN)")
    public ResponseEntity<Void> delete(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey) {
        connectorService.delete(tenantCode, connectorKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectorKey}/api-key")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Register ERP API key", description = "Hashes rawKey, registers with ERP, stores encrypted for outbound calls. Shows raw key once — never retrievable after.")
    public ResponseEntity<Map<String, String>> registerApiKey(
            @RequestParam String tenantCode,
            @PathVariable String connectorKey,
            @Valid @RequestBody ConnectorApiKeyRequest request,
            @RequestHeader("Authorization") String authorization) {
        connectorService.registerApiKey(tenantCode, connectorKey, request, authorization);
        return ResponseEntity.ok(Map.of("status", "registered"));
    }
}
