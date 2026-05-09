package com.werkflow.admin.designtime.connector.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.werkflow.admin.designtime.connector.dto.ConnectorSummary;
import com.werkflow.admin.designtime.connector.dto.FlatField;
import com.werkflow.admin.designtime.connector.dto.OpenApiImportRequest;
import com.werkflow.admin.designtime.connector.dto.OperationSummary;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.admin.designtime.connector.service.ConnectorCatalogService;
import com.werkflow.admin.designtime.connector.service.DesignAuditService;
import com.werkflow.admin.designtime.connector.service.OpenApiImportService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Design-Time Data Service (DTDS) — connector catalog REST endpoints.
 *
 * <p>All endpoints are tenant-scoped via the caller's JWT {@code tenant_id} claim.
 * Cross-tenant access is not possible through these endpoints — the service layer
 * enforces isolation and returns 403 on violations.</p>
 *
 * <p>Every successful call is recorded asynchronously in the {@code design_audit_log}.</p>
 */
@RestController
@RequestMapping("/api/v1/design")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
@Tag(name = "DTDS — Connector Catalog", description = "Design-Time Data Service: connector catalog, schema resolution, and field enumeration")
public class DesignTimeDataController {

    private final ConnectorCatalogService catalogService;
    private final OpenApiImportService openApiImportService;
    private final DesignAuditService auditService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // -------------------------------------------------------------------------
    // Connector listing
    // -------------------------------------------------------------------------

    @GetMapping("/connectors")
    @Operation(summary = "List connectors",
               description = "Returns a paginated list of connector summaries visible to the caller's tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connector list returned"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ResponseEntity<Page<ConnectorSummary>> listConnectors(
            @PageableDefault(size = 20, sort = "key") Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = tenant(jwt);
        Page<ConnectorSummary> page = catalogService.list(tenantId, pageable);
        auditService.record(tenantId, principal(jwt), "/api/v1/design/connectors",
                null, null, null);
        return ResponseEntity.ok(page);
    }

    // -------------------------------------------------------------------------
    // Full definition
    // -------------------------------------------------------------------------

    @GetMapping("/connectors/{key}")
    @Operation(summary = "Get connector definition",
               description = "Returns the full ConnectorDefinition JSON (secrets redacted) for the latest version of the connector.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connector definition returned"),
        @ApiResponse(responseCode = "404", description = "Connector not found"),
        @ApiResponse(responseCode = "403", description = "Cross-tenant access denied")
    })
    public ResponseEntity<String> getConnector(
            @Parameter(description = "Connector key (kebab-case)") @PathVariable String key,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = tenant(jwt);
        String definition = catalogService.getDefinitionJson(tenantId, key);
        auditService.record(tenantId, principal(jwt), "/api/v1/design/connectors/" + key,
                key, null, null);
        return ResponseEntity.ok(definition);
    }

    // -------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------

    @GetMapping("/connectors/{key}/operations")
    @Operation(summary = "List connector operations",
               description = "Returns metadata for all operations declared in the connector definition.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operations returned"),
        @ApiResponse(responseCode = "404", description = "Connector not found")
    })
    public ResponseEntity<List<OperationSummary>> listOperations(
            @PathVariable String key,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = tenant(jwt);
        List<OperationSummary> ops = catalogService.listOperations(tenantId, key);
        auditService.record(tenantId, principal(jwt), "/api/v1/design/connectors/" + key + "/operations",
                key, null, null);
        return ResponseEntity.ok(ops);
    }

    // -------------------------------------------------------------------------
    // Schema resolution
    // -------------------------------------------------------------------------

    @GetMapping("/connectors/{key}/operations/{opId}/schema")
    @Operation(summary = "Get resolved operation schema",
               description = "Returns the resolved JSON Schema for the operation's input or output. $refs are inlined.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema returned"),
        @ApiResponse(responseCode = "400", description = "Invalid direction parameter"),
        @ApiResponse(responseCode = "404", description = "Connector or operation not found")
    })
    public ResponseEntity<JsonNode> getOperationSchema(
            @PathVariable String key,
            @PathVariable String opId,
            @RequestParam(defaultValue = "input") String direction,
            @AuthenticationPrincipal Jwt jwt) {
        validateDirection(direction);
        String tenantId = tenant(jwt);
        JsonNode schema = catalogService.getOperationSchema(tenantId, key, opId, direction);
        auditService.record(tenantId, principal(jwt),
                "/api/v1/design/connectors/" + key + "/operations/" + opId + "/schema",
                key, opId, direction);
        return ResponseEntity.ok(schema);
    }

    // -------------------------------------------------------------------------
    // Flat field list
    // -------------------------------------------------------------------------

    @GetMapping("/connectors/{key}/operations/{opId}/fields")
    @Operation(summary = "Get flat field list",
               description = "Returns the flattened field descriptors for an operation's input or output schema.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Field list returned"),
        @ApiResponse(responseCode = "404", description = "Connector or operation not found")
    })
    public ResponseEntity<List<FlatField>> getFlatFields(
            @PathVariable String key,
            @PathVariable String opId,
            @RequestParam(defaultValue = "input") String direction,
            @AuthenticationPrincipal Jwt jwt) {
        validateDirection(direction);
        String tenantId = tenant(jwt);
        List<FlatField> fields = catalogService.getFlatFields(tenantId, key, opId, direction);
        auditService.record(tenantId, principal(jwt),
                "/api/v1/design/connectors/" + key + "/operations/" + opId + "/fields",
                key, opId, direction);
        return ResponseEntity.ok(fields);
    }

    // -------------------------------------------------------------------------
    // OpenAPI import
    // -------------------------------------------------------------------------

    @PostMapping("/connectors/import-openapi")
    @Operation(summary = "Import from OpenAPI",
               description = "Accepts an OpenAPI 3.1 YAML/JSON document and auto-generates a ConnectorDefinition with one operation per path×method.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Connector definition created from OpenAPI document"),
        @ApiResponse(responseCode = "400", description = "Invalid OpenAPI document or request"),
        @ApiResponse(responseCode = "409", description = "Connector key+version already registered")
    })
    public ResponseEntity<ConnectorDefinitionV2> importOpenApi(
            @Valid @RequestBody OpenApiImportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = tenant(jwt);
        ConnectorDefinitionV2 result = openApiImportService.importOpenApi(tenantId, request);
        result.setDefinitionJson(catalogService.redactDefinitionJson(result.getDefinitionJson()));
        auditService.record(tenantId, principal(jwt),
                "/api/v1/design/connectors/import-openapi", result.getKey(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String tenant(Jwt jwt) {
        return jwtClaimsExtractor.getTenantId(jwt);
    }

    private String principal(Jwt jwt) {
        String username = jwtClaimsExtractor.getUsername(jwt);
        return username != null ? username : jwtClaimsExtractor.getUserId(jwt);
    }

    private static void validateDirection(String direction) {
        if (!"input".equals(direction) && !"output".equals(direction)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "direction must be 'input' or 'output'");
        }
    }
}
