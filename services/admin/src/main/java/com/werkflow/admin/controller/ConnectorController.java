package com.werkflow.admin.controller;

import com.werkflow.admin.dto.connector.*;
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
    @PreAuthorize("isAuthenticated()")
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
}
