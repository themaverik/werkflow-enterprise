package com.werkflow.admin.designtime.connector.generator;

import com.werkflow.admin.designtime.connector.dto.OpenApiImportRequest;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.admin.designtime.connector.service.ConnectorCatalogService;
import com.werkflow.admin.designtime.connector.service.DesignAuditService;
import com.werkflow.admin.designtime.connector.service.OpenApiImportService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Connector generator endpoints — create ConnectorDefinitions from external formats.
 *
 * <p>The old {@code POST /api/v1/design/connectors/import-openapi} route is preserved
 * on {@link com.werkflow.admin.designtime.connector.controller.DesignTimeDataController}
 * for backward compatibility. These routes are the canonical new location.</p>
 */
@RestController
@RequestMapping("/api/v1/connectors/generators")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
@Tag(name = "Connector Generators", description = "Generate ConnectorDefinitions from OpenAPI specs or JSON Schemas")
public class ConnectorGeneratorController {

    private final OpenApiImportService openApiImportService;
    private final JsonSchemaConnectorGeneratorService jsonSchemaGeneratorService;
    private final ConnectorCatalogService catalogService;
    private final DesignAuditService auditService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // -------------------------------------------------------------------------
    // OpenAPI generator (canonical route — old route preserved for backward compat)
    // -------------------------------------------------------------------------

    @PostMapping("/openapi")
    @Operation(
        summary = "Generate connector from OpenAPI",
        description = "Accepts an OpenAPI 3.1 YAML/JSON document and generates a ConnectorDefinition " +
                      "with one operation per path × method. " +
                      "Canonical route — equivalent to POST /api/v1/design/connectors/import-openapi."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Connector definition created"),
        @ApiResponse(responseCode = "400", description = "Invalid OpenAPI document"),
        @ApiResponse(responseCode = "409", description = "Connector key+version already registered")
    })
    public ResponseEntity<ConnectorDefinitionV2> generateFromOpenApi(
            @Valid @RequestBody OpenApiImportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        ConnectorDefinitionV2 result = openApiImportService.importOpenApi(tenantId, request);
        result.setDefinitionJson(catalogService.redactDefinitionJson(result.getDefinitionJson()));
        auditService.record(tenantId, principal(jwt), "/api/v1/connectors/generators/openapi",
                result.getKey(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // -------------------------------------------------------------------------
    // JSON Schema generator
    // -------------------------------------------------------------------------

    @PostMapping("/json-schema")
    @Operation(
        summary = "Generate connector stub from JSON Schema",
        description = "Accepts a JSON Schema document and creates a single-operation REST connector stub. " +
                      "The generated connector has transport.type=rest with a placeholder baseUrl; " +
                      "configure the actual endpoint and auth after import. " +
                      "Useful for rapid integration testing and demos."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Connector stub created"),
        @ApiResponse(responseCode = "400", description = "Invalid JSON Schema or request"),
        @ApiResponse(responseCode = "409", description = "Connector key already registered")
    })
    public ResponseEntity<ConnectorDefinitionV2> generateFromJsonSchema(
            @Valid @RequestBody JsonSchemaGeneratorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        ConnectorDefinitionV2 result = jsonSchemaGeneratorService.generate(tenantId, request);
        result.setDefinitionJson(catalogService.redactDefinitionJson(result.getDefinitionJson()));
        auditService.record(tenantId, principal(jwt), "/api/v1/connectors/generators/json-schema",
                result.getKey(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private String principal(Jwt jwt) {
        String username = jwtClaimsExtractor.getUsername(jwt);
        return username != null ? username : jwtClaimsExtractor.getUserId(jwt);
    }
}
